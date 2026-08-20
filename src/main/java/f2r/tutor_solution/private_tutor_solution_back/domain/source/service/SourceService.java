package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.StudentAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.score.repository.StudentScoreRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.SourceUploadRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ContentKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadedFileIngestor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 학생 업로드 파일의 도메인 로직(이미지 재인코딩, PDF 렌더링)을 담당하고,
 * 실제 영속화는 {@link FileStorage} 구현체(local / s3)에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {

  private final SourceRepository sourceRepository;
  private final SourceAnalysisService sourceAnalysisService;
  private final FileStorage fileStorage;
  private final ConversationSourceRepository conversationSourceRepository;
  private final StudentScoreRepository studentScoreRepository;
  private final StudentAnswerRepository studentAnswerRepository;
  private final StudentRepository studentRepository;
  private final AuthenticatedUserResolver authResolver;
  private final ImageSanitizer imageSanitizer;
  private final UploadedFileIngestor uploadedFileIngestor;

  /**
   * 한 묶음(한 시험 단위)의 여러 파일을 저장한다.
   * 모든 파일은 같은 contentKind/메타로 처리되며, 분석 트리거는 묶음 단위로 1회만 등록한다.
   */
  @Transactional
  public List<Source> saveBatch(
      List<MultipartFile> files, User uploader, SourceType sourceType, SourceUploadRequest request) {
    ContentKind kind = (request != null && request.contentKind() != null)
        ? request.contentKind()
        : ContentKind.GENERAL;

    List<Source> saved = new ArrayList<>(files.size());
    for (MultipartFile file : files) {
      String fileType = uploadedFileIngestor.resolveFileType(file);
      UploadedFileIngestor.StoredFile stored = uploadedFileIngestor.store(file, uploader.getUserId(), fileType);

      Source source = Source.builder()
          .uploadedBy(uploader)
          .originalFileName(file.getOriginalFilename())
          .storageKey(stored.storageKey())
          .fileType(stored.fileType())
          .fileSize(stored.fileSize())
          .sourceType(sourceType)
          .contentKind(kind)
          .build();
      saved.add(sourceRepository.save(source));
    }

    List<Long> sourceIds = saved.stream().map(Source::getSourceId).toList();
    triggerAsyncBatchAnalysisAfterCommit(sourceIds, request);
    return saved;
  }

  /**
   * 분석 파이프라인을 타지 않는 단일 파일 저장.
   * 오답노트/피드백처럼 파일 보관·미리보기만 필요한 흐름에서 사용한다.
   */
  @Transactional
  public Source saveManualFile(MultipartFile file, User uploader, SourceType sourceType, ContentKind contentKind) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("업로드할 파일이 필요합니다.");
    }
    String fileType = uploadedFileIngestor.resolveFileType(file);
    UploadedFileIngestor.StoredFile stored = uploadedFileIngestor.store(file, uploader.getUserId(), fileType);
    Source source = Source.builder()
        .uploadedBy(uploader)
        .originalFileName(file.getOriginalFilename())
        .storageKey(stored.storageKey())
        .fileType(stored.fileType())
        .fileSize(stored.fileSize())
        .sourceType(sourceType)
        .contentKind(contentKind != null ? contentKind : ContentKind.GENERAL)
        .build();
    source.updateProcessingStatus(f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.ProcessingStatus.COMPLETED);
    return sourceRepository.save(source);
  }

  private void triggerAsyncBatchAnalysisAfterCommit(List<Long> sourceIds, SourceUploadRequest request) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        sourceAnalysisService.analyzeBatchAsync(sourceIds, request);
      }
    });
  }

  static int readExifOrientation(byte[] bytes) {
    return ImageSanitizer.readExifOrientation(bytes);
  }

  static BufferedImage applyExifOrientation(BufferedImage src, int orientation) {
    return ImageSanitizer.applyExifOrientation(src, orientation);
  }

  /** source의 페이지 개수. IMAGE/UNKNOWN은 1, PDF는 페이지 수. */
  public int pageCountOf(Source src) {
    return fileStorage.pageCount(src.getStorageKey(), src.getFileType());
  }

  /** 라이트박스 미리보기용 — source의 N번째 페이지(또는 이미지 본체) bytes 반환. */
  public byte[] readPageBytes(Source src, int page) throws IOException {
    return fileStorage.readPage(src.getStorageKey(), src.getFileType(), page);
  }

  /**
   * 자료 영구 삭제. cascade 동작:
   * <ol>
   *   <li>ownership 검증 (본인 또는 ADMIN)</li>
   *   <li>conversation_sources 매핑 제거 (이 source가 첨부됐던 모든 대화에서 해제)</li>
   *   <li>시험지(exam 연결)였다면 학생의 student_scores(source_id 기반) + student_answers((student_id, exam_id) 기반) 제거</li>
   *   <li>sources row 제거</li>
   *   <li>storage 파일 제거 (best-effort — 실패해도 트랜잭션은 진행)</li>
   * </ol>
   *
   * <p>exam 마스터, questions, passages는 다른 학생이 공유할 수 있으므로 보존한다.
   */
  @Transactional
  public void delete(Long sourceId) {
    deleteInternal(sourceId, true);
  }

  /**
   * conversation 접근 권한을 이미 검증한 상위 도메인에서 source 소유자 검증 없이 정리할 때 사용한다.
   */
  @Transactional
  public void deleteTrusted(Long sourceId) {
    deleteInternal(sourceId, false);
  }

  private void deleteInternal(Long sourceId, boolean verifyOwner) {
    Source source = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("source not found: " + sourceId));
    if (verifyOwner) {
      authResolver.verifyOwnsSource(source);
    }

    // 1) 대화 첨부 매핑 정리
    conversationSourceRepository.deleteBySourceId(sourceId);

    // 2) 시험지였으면 채점 데이터 cascade
    if (source.getExam() != null) {
      Long examId = source.getExam().getExamId();
      Long uploaderUserId = source.getUploadedBy().getUserId();
      Student student = studentRepository.findByUser_UserId(uploaderUserId).orElse(null);
      if (student != null) {
        studentScoreRepository.deleteBySource_SourceId(sourceId);
        studentAnswerRepository.deleteByStudent_StudentIdAndExamId(student.getStudentId(), examId);
      }
    }

    // 3) source row 삭제
    String storageKey = source.getStorageKey();
    String fileType   = source.getFileType();
    sourceRepository.delete(source);

    // 4) storage 파일 정리 (실패해도 DB 변경은 commit)
    fileStorage.delete(storageKey, fileType);
  }

  /** 테스트 접근용 래퍼. 업로드 저장 경로와 동일하게 {@code sanitize}를 거친다. */
  byte[] reencodeImageForTest(MultipartFile file) throws IOException {
    return imageSanitizer.sanitize(file).bytes();
  }

  /** 테스트 접근용 래퍼 */
  List<byte[]> renderPdfPagesForTest(MultipartFile file) throws IOException {
    return uploadedFileIngestor.renderPdfPages(file);
  }
}
