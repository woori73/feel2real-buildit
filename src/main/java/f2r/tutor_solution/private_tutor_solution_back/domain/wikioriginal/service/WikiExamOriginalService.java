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

  /** 모의고사 자료 문서 — PDF/HWP/HWPX/HTML + 해설 이미지(PNG/JPG/WebP). */
  private static final Set<String> ORIGINAL_EXTENSIONS =
      Set.of("pdf", "hwp", "hwpx", "html", "htm", "png", "jpg", "jpeg", "webp");

  /** 브라우저 인라인 열람을 허용하는 확장자 (이미지 + HTML). HTML은 CSP sandbox로 격리해 서빙한다. */
  public static final Set<String> VIEWABLE_EXTENSIONS = Set.of("html", "htm", "png", "jpg", "jpeg", "webp");

  private final WikiExamOriginalRepository repository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;
  /** 모의고사 자료 전용 상한 — 해설 HTML은 내장 폰트·이미지 때문에 문서 기본 상한(20MB)보다 크다. */
  private final long maxOriginalBytes;

  public WikiExamOriginalService(WikiExamOriginalRepository repository, FileStorage fileStorage,
      DocumentUploadValidator documentUploadValidator,
      @Value("${app.upload.max-wiki-original-bytes:52428800}") long maxOriginalBytes) {
    this.repository = repository;
    this.fileStorage = fileStorage;
    this.documentUploadValidator = documentUploadValidator;
    this.maxOriginalBytes = maxOriginalBytes;
  }

  public record DownloadFile(byte[] bytes, String fileName, String title) {
    /** 하위호환: 다운로드 통계용 title 없이 생성 — fileName을 title로 사용. */
    public DownloadFile(byte[] bytes, String fileName) {
      this(bytes, fileName, fileName);
    }
  }

  @Transactional(readOnly = true)
  public List<WikiExamOriginalResponse> searchPublic(Integer grade) {
    return repository.findPublicByGrade(grade).stream().map(WikiExamOriginalResponse::of).toList();
  }

  /** 회차·종류 필터 공개 목록 (메인 회차 해설 섹션·아카이브). docType이 비면 전체 종류. */
  @Transactional(readOnly = true)
  public List<WikiExamOriginalResponse> searchPublicFiltered(
      Integer grade, Integer examYear, Integer examMonth, String docType) {
    WikiExamOriginalDocType parsed = null;
    if (docType != null && !docType.isBlank()) {
      parsed = parseDocType(docType);
    }
    return repository.findPublicFiltered(grade, examYear, examMonth, parsed).stream()
        .map(WikiExamOriginalResponse::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public DownloadFile getPublicDownload(Long id) {
    WikiExamOriginal original = repository.findPublicById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 원본 자료를 찾을 수 없습니다."));
    try {
      byte[] bytes = fileStorage.readSingleLimited(original.getStorageKey(), RAW_FILE_TYPE, maxOriginalBytes);
      return new DownloadFile(bytes, original.getOriginalFileName(), original.getTitle());
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
    return create(title, grade, examLabel, docType, visible, file, null, null);
  }

  /**
   * 회차(연도·월)를 받는 등록. examYear/examMonth가 있으면 exam_label은 서버가
   * "{year} {month}월 모의고사"로 합성한다(하위호환: 라벨만 온 기존 호출은 그대로 저장).
   */
  @Transactional
  public WikiExamOriginalResponse create(String title, Integer grade, String examLabel, String docType,
      boolean visible, MultipartFile file, Integer examYear, Integer examMonth) {
    documentUploadValidator.validate(file, ORIGINAL_EXTENSIONS, maxOriginalBytes);
    String storageKey = saveFile(file);
    WikiExamOriginal original = WikiExamOriginal.builder()
        .title(required(title, "제목"))
        .grade(validGrade(grade))
        .examLabel(resolveExamLabel(examLabel, examYear, examMonth))
        .examYear(validExamYear(examYear))
        .examMonth(validExamMonth(examMonth))
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
    return update(id, title, grade, examLabel, docType, visible, file, null, null);
  }

  @Transactional
  public WikiExamOriginalResponse update(Long id, String title, Integer grade, String examLabel,
      String docType, boolean visible, MultipartFile file, Integer examYear, Integer examMonth) {
    WikiExamOriginal original = getAdminEntity(id);
    // 연도·월이 모두 비어 온 수정(기존 호출)은 회차 값을 지우지 않고 기존 값을 유지한다.
    Integer keepYear = examYear == null && examMonth == null ? original.getExamYear() : validExamYear(examYear);
    Integer keepMonth = examYear == null && examMonth == null ? original.getExamMonth() : validExamMonth(examMonth);
    original.updateMetadata(
        required(title, "제목"),
        validGrade(grade),
        resolveExamLabel(examLabel, examYear, examMonth),
        parseDocType(docType),
        visible,
        keepYear,
        keepMonth);
    if (file != null && !file.isEmpty()) {
      documentUploadValidator.validate(file, ORIGINAL_EXTENSIONS, maxOriginalBytes);
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

  /** 인라인 열람용 파일 — HTML/이미지만 허용하고, 그 외 형식은 다운로드로 안내한다. */
  @Transactional(readOnly = true)
  public DownloadFile getPublicView(Long id) {
    WikiExamOriginal original = repository.findPublicById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 자료를 찾을 수 없습니다."));
    String ext = extensionOf(original.getOriginalFileName());
    if (ext == null || !VIEWABLE_EXTENSIONS.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 형식은 열람을 지원하지 않습니다. 다운로드해 주세요.");
    }
    try {
      byte[] bytes = fileStorage.readSingleLimited(original.getStorageKey(), RAW_FILE_TYPE, maxOriginalBytes);
      return new DownloadFile(bytes, original.getOriginalFileName(), original.getTitle());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 파일을 찾을 수 없습니다.", e);
    }
  }

  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return null;
    }
    int dot = fileName.lastIndexOf('.');
    return dot < 0 || dot == fileName.length() - 1
        ? null
        : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  /** 연도·월이 오면 라벨을 합성하고, 아니면 기존처럼 라벨을 필수로 받는다. 둘 다 없으면 400. */
  private String resolveExamLabel(String examLabel, Integer examYear, Integer examMonth) {
    if (examYear != null && examMonth != null) {
      return examYear + " " + examMonth + "월 모의고사";
    }
    return required(examLabel, "시험 이름");
  }

  private Integer validExamYear(Integer examYear) {
    if (examYear == null) {
      return null;
    }
    if (examYear < 2000 || examYear > 2100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 연도는 2000~2100 사이로 입력해주세요.");
    }
    return examYear;
  }

  private Integer validExamMonth(Integer examMonth) {
    if (examMonth == null) {
      return null;
    }
    if (examMonth < 1 || examMonth > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 시행 월은 1~12 사이로 입력해주세요.");
    }
    return examMonth;
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
