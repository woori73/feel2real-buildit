package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamOriginalResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginalDocType;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 BuildIt Wiki 모의고사 원본 관리. {@code ExamAnalysisService} 패턴을 따른다.
 * 공개: 학년별 목록 + 다운로드. 관리(ADMIN): 등록/수정/공개전환/삭제.
 */
@Service
public class WikiExamOriginalService {

  /** FileStorage에 원본 그대로 저장/조회할 때 쓰는 비-PDF 페이지 분해 없는 타입. */
  private static final String RAW_FILE_TYPE = "UNKNOWN";
  private static final long STORAGE_OWNER_ID = 0L;

  /** 모의고사 원본 문서 — PDF/HWP/HWPX + HTML. */
  private static final Set<String> ORIGINAL_EXTENSIONS = Set.of("pdf", "hwp", "hwpx", "html", "htm");

  private final WikiExamOriginalRepository repository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;
  private final long maxDocumentBytes;

  public WikiExamOriginalService(WikiExamOriginalRepository repository, FileStorage fileStorage,
      DocumentUploadValidator documentUploadValidator,
      @Value("${app.upload.max-document-bytes:20971520}") long maxDocumentBytes) {
    this.repository = repository;
    this.fileStorage = fileStorage;
    this.documentUploadValidator = documentUploadValidator;
    this.maxDocumentBytes = maxDocumentBytes;
  }

  public record DownloadFile(byte[] bytes, String fileName) {}

  @Transactional(readOnly = true)
  public List<WikiExamOriginalResponse> searchPublic(Integer grade) {
    return repository.findPublicByGrade(grade).stream().map(WikiExamOriginalResponse::of).toList();
  }

  @Transactional(readOnly = true)
  public DownloadFile getPublicDownload(Long id) {
    WikiExamOriginal original = repository.findPublicById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 원본 자료를 찾을 수 없습니다."));
    try {
      byte[] bytes = fileStorage.readSingleLimited(original.getStorageKey(), RAW_FILE_TYPE, maxDocumentBytes);
      return new DownloadFile(bytes, original.getOriginalFileName());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 파일을 찾을 수 없습니다.", e);
    }
  }

  @Transactional(readOnly = true)
  public List<WikiExamOriginalResponse> listAdmin() {
    return repository.findAdminList().stream().map(WikiExamOriginalResponse::of).toList();
  }

  @Transactional
  public WikiExamOriginalResponse create(String title, Integer grade, String examLabel, String docType,
      boolean visible, MultipartFile file) {
    documentUploadValidator.validate(file, ORIGINAL_EXTENSIONS);
    String storageKey = saveFile(file);
    WikiExamOriginal original = WikiExamOriginal.builder()
        .title(required(title, "제목"))
        .grade(validGrade(grade))
        .examLabel(required(examLabel, "시험 이름"))
        .docType(parseDocType(docType))
        .originalFileName(originalFileName(file))
        .storageKey(storageKey)
        .fileSize(file.getSize())
        .visible(visible)
        .build();
    return WikiExamOriginalResponse.of(repository.save(original));
  }

  @Transactional
  public WikiExamOriginalResponse update(Long id, String title, Integer grade, String examLabel,
      String docType, boolean visible, MultipartFile file) {
    WikiExamOriginal original = getAdminEntity(id);
    original.updateMetadata(
        required(title, "제목"),
        validGrade(grade),
        required(examLabel, "시험 이름"),
        parseDocType(docType),
        visible);
    if (file != null && !file.isEmpty()) {
      documentUploadValidator.validate(file, ORIGINAL_EXTENSIONS);
      String newStorageKey = saveFile(file);
      String oldStorageKey = original.replaceFile(originalFileName(file), newStorageKey, file.getSize());
      fileStorage.delete(oldStorageKey, RAW_FILE_TYPE);
    }
    return WikiExamOriginalResponse.of(original);
  }

  @Transactional
  public WikiExamOriginalResponse updateVisibility(Long id, boolean visible) {
    WikiExamOriginal original = getAdminEntity(id);
    original.setVisible(visible);
    return WikiExamOriginalResponse.of(original);
  }

  @Transactional
  public void softDelete(Long id) {
    getAdminEntity(id).softDelete();
  }

  @Transactional
  public void hardDelete(Long id) {
    WikiExamOriginal original = getAdminEntity(id);
    String storageKey = original.getStorageKey();
    repository.delete(original);
    fileStorage.delete(storageKey, RAW_FILE_TYPE);
  }

  private WikiExamOriginal getAdminEntity(Long id) {
    return repository.findAdminById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 자료를 찾을 수 없습니다."));
  }

  private String saveFile(MultipartFile file) {
    try {
      return fileStorage.saveRaw(STORAGE_OWNER_ID, originalFileName(file), file.getInputStream(), file.getSize());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "원본 파일 저장에 실패했습니다.", e);
    }
  }

  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename();
    return (filename == null || filename.isBlank()) ? "original.pdf" : filename;
  }

  private WikiExamOriginalDocType parseDocType(String value) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자료 구분(문제지/정답지)을 선택해주세요.");
    }
    try {
      return WikiExamOriginalDocType.valueOf(trimmed);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 자료 구분입니다: " + value);
    }
  }

  private Integer validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~3(고1~고3) 사이 숫자로 입력해주세요.");
    }
    return grade;
  }

  private String required(String value, String label) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해주세요.");
    }
    return trimmed;
  }

  private String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
