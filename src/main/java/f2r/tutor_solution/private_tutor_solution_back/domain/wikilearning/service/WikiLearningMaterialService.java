package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto.WikiLearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.entity.WikiLearningMaterialType;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.repository.WikiLearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 BuildIt Wiki "모의고사 학습 자료" — 일반 회원 업로드 자료 관리.
 *
 * <p>{@code WikiAnalysisMaterialService} 패턴을 따르되, 권한이 다르다:
 * <ul>
 *   <li>목록(제목 포함): 공개.
 *   <li>다운로드: 회원(로그인) 전용 — 컨트롤러가 {@code /api/**} 인증 게이트로 막고, 여기서는 인증 여부를 가정하지 않는다.
 *   <li>업로드: 회원 누구나. uploader 는 파라미터의 {@code uploaderUserId}(= authenticated principal) 로만 기록한다.
 *   <li>삭제/전체 관리: ADMIN 전용({@code /api/admin/**}).
 * </ul>
 *
 * <p>파일 검증은 전역 자료실 정책({@link DocumentUploadValidator#validate(MultipartFile)} — 문서 확장자
 * allowlist + 매직바이트 + 실행파일 차단 + 용량 상한)을 그대로 쓴다. 이미지·HTML 은 받지 않는다.
 */
@Service
public class WikiLearningMaterialService {

  private static final String RAW_FILE_TYPE = "UNKNOWN";
  private static final long STORAGE_OWNER_ID = 0L;
  private static final int MAX_TITLE_LENGTH = 200;

  private final WikiLearningMaterialRepository repository;
  private final UserRepository userRepository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;
  private final long maxDocumentBytes;

  public WikiLearningMaterialService(WikiLearningMaterialRepository repository,
      UserRepository userRepository, FileStorage fileStorage,
      DocumentUploadValidator documentUploadValidator,
      @Value("${app.upload.max-document-bytes:20971520}") long maxDocumentBytes) {
    this.repository = repository;
    this.userRepository = userRepository;
    this.fileStorage = fileStorage;
    this.documentUploadValidator = documentUploadValidator;
    this.maxDocumentBytes = maxDocumentBytes;
  }

  public record DownloadFile(byte[] bytes, String fileName, String contentType) {}

  @Transactional(readOnly = true)
  public List<WikiLearningMaterialResponse> searchPublic(Integer grade) {
    return repository.findPublicByGrade(grade).stream().map(WikiLearningMaterialResponse::of).toList();
  }

  @Transactional(readOnly = true)
  public List<WikiLearningMaterialResponse> listAdmin() {
    return repository.findAdminList().stream().map(WikiLearningMaterialResponse::of).toList();
  }

  /** 회원(로그인) 전용 다운로드. 인증 검사는 컨트롤러/시큐리티가 이미 끝낸 상태로 진입한다. */
  @Transactional(readOnly = true)
  public DownloadFile getDownload(Long id) {
    WikiLearningMaterial material = repository.findActiveById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료를 찾을 수 없습니다."));
    try {
      byte[] bytes = fileStorage.readSingleLimited(material.getStorageKey(), RAW_FILE_TYPE, maxDocumentBytes);
      return new DownloadFile(bytes, material.getOriginalFileName(), material.getContentType());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료 파일을 찾을 수 없습니다.", e);
    }
  }

  /**
   * 회원 업로드. {@code uploaderUserId} 는 반드시 컨트롤러가 authenticated principal 에서 넘긴 값이어야 한다
   * (요청 본문의 uploader 값을 신뢰하지 않는다). 학년은 현재 Wiki 탭에서 자동으로 온다.
   */
  @Transactional
  public WikiLearningMaterialResponse upload(Long uploaderUserId, String dataType, Integer grade,
      String title, MultipartFile file) {
    User uploader = userRepository.findById(uploaderUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

    // 저장소를 건드리기 전에 모든 입력을 먼저 검증한다(실패 시 고아 파일 방지).
    WikiLearningMaterialType type = WikiLearningMaterialType.parse(dataType);
    Integer validGrade = validGrade(grade);
    documentUploadValidator.validate(file);
    String fileName = originalFileName(file);
    String resolvedTitle = resolveTitle(title, fileName);
    String storageKey = saveFile(file, fileName);

    WikiLearningMaterial material = WikiLearningMaterial.builder()
        .dataType(type)
        .grade(validGrade)
        .title(resolvedTitle)
        .contentType(DocumentUploadValidator.contentType(fileName))
        .originalFileName(fileName)
        .storageKey(storageKey)
        .fileSize(file.getSize())
        .uploaderUserId(uploader.getUserId())
        .uploaderName(uploader.getDisplayName())
        .build();
    return WikiLearningMaterialResponse.of(repository.save(material));
  }

  /** ADMIN 전용 완전 삭제 — row + storage. */
  @Transactional
  public void adminHardDelete(Long id) {
    WikiLearningMaterial material = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료를 찾을 수 없습니다."));
    String storageKey = material.getStorageKey();
    repository.delete(material);
    fileStorage.delete(storageKey, RAW_FILE_TYPE);
  }

  private String saveFile(MultipartFile file, String name) {
    try {
      return fileStorage.saveRaw(STORAGE_OWNER_ID, name, file.getInputStream(), file.getSize());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "학습 자료 파일 저장에 실패했습니다.", e);
    }
  }

  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename();
    return (filename == null || filename.isBlank()) ? "material" : filename;
  }

  /** 사용자가 제목을 비우면 파일명에서 자동 생성한다(확장자 제거 + '_' → 공백 + 공백 정리). */
  private String resolveTitle(String title, String fileName) {
    String trimmed = title == null ? "" : title.trim();
    if (trimmed.isEmpty()) {
      trimmed = titleFromFileName(fileName);
    }
    if (trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해주세요.");
    }
    return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
  }

  private String titleFromFileName(String fileName) {
    String base = fileName;
    int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    int dot = base.lastIndexOf('.');
    if (dot > 0) {
      base = base.substring(0, dot);
    }
    return base.replace('_', ' ').replaceAll("\\s+", " ").trim();
  }

  private Integer validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~3(고1~고3) 사이 숫자로 입력해주세요.");
    }
    return grade;
  }
}
