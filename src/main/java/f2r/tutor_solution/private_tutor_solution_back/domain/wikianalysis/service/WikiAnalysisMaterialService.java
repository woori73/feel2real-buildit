package f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto.WikiAnalysisMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.TransactionalFileDeletion;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공개 BuildIt Wiki 분석 자료 문서 관리. {@code WikiExamOriginalService} 패턴을 따른다.
 * 공개: 학년별 목록 + 다운로드. 관리(ADMIN): 등록/수정/공개전환/삭제.
 */
@Service
public class WikiAnalysisMaterialService {

  private static final String RAW_FILE_TYPE = "UNKNOWN";
  private static final long STORAGE_OWNER_ID = 0L;
  private static final String DEFAULT_SUBJECT = "영어";

  private final WikiAnalysisMaterialRepository repository;
  private final FileStorage fileStorage;
  private final DocumentUploadValidator documentUploadValidator;
  private final long maxDocumentBytes;

  public WikiAnalysisMaterialService(WikiAnalysisMaterialRepository repository, FileStorage fileStorage,
      DocumentUploadValidator documentUploadValidator,
      @Value("${app.upload.max-document-bytes:20971520}") long maxDocumentBytes) {
    this.repository = repository;
    this.fileStorage = fileStorage;
    this.documentUploadValidator = documentUploadValidator;
    this.maxDocumentBytes = maxDocumentBytes;
  }

  public record DownloadFile(byte[] bytes, String fileName, String contentType, String title) {
    /** 하위호환: 다운로드 통계용 title 없이 생성 — fileName을 title로 사용. */
    public DownloadFile(byte[] bytes, String fileName, String contentType) {
      this(bytes, fileName, contentType, fileName);
    }
  }

  @Transactional(readOnly = true)
  public List<WikiAnalysisMaterialResponse> searchPublic(Integer grade) {
    return repository.findPublicByGrade(grade).stream().map(WikiAnalysisMaterialResponse::of).toList();
  }

  @Transactional(readOnly = true)
  public DownloadFile getPublicDownload(Long id) {
    WikiAnalysisMaterial material = repository.findPublicById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 분석 자료를 찾을 수 없습니다."));
    try {
      byte[] bytes = fileStorage.readSingleLimited(material.getStorageKey(), RAW_FILE_TYPE, maxDocumentBytes);
      return new DownloadFile(bytes, material.getOriginalFileName(), material.getContentType(), material.getTitle());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 자료 파일을 찾을 수 없습니다.", e);
    }
  }

  @Transactional(readOnly = true)
  public List<WikiAnalysisMaterialResponse> listAdmin() {
    return repository.findAdminList().stream().map(WikiAnalysisMaterialResponse::of).toList();
  }

  @Transactional
  public WikiAnalysisMaterialResponse create(String title, String description, String schoolName, Integer grade,
      Integer year, String semester, String examType, String subject, boolean visible, MultipartFile file) {
    return create(title, description, schoolName, grade, year, semester, examType, subject, visible, file, null);
  }

  /**
   * 확장자 화이트리스트를 넓혀서 저장한다. {@code allowedExtensions == null} 이면 기본 문서 정책.
   * Question Wiki ADMIN "직접 올리기"가 {@link DocumentUploadValidator#DOCUMENT_WITH_HTML_EXTENSIONS}
   * (문서 + HTML)를 넘겨 이 경로에서만 HTML 을 허용한다 — 전역 자료실 정책은 그대로다.
   */
  @Transactional
  public WikiAnalysisMaterialResponse create(String title, String description, String schoolName, Integer grade,
      Integer year, String semester, String examType, String subject, boolean visible, MultipartFile file,
      java.util.Set<String> allowedExtensions) {
    if (allowedExtensions == null) {
      documentUploadValidator.validate(file);
    } else {
      documentUploadValidator.validate(file, allowedExtensions);
    }
    String name = originalFileName(file);
    String storageKey = saveFile(file, name);
    WikiAnalysisMaterial material = WikiAnalysisMaterial.builder()
        .title(required(title, "제목"))
        .description(blankToNull(description))
        .schoolName(blankToNull(schoolName))
        .grade(validGrade(grade))
        .year(validYear(year))
        .semester(required(semester, "학기"))
        .examType(required(examType, "시험"))
        .subject(subjectOrDefault(subject))
        .contentType(DocumentUploadValidator.contentType(name))
        .originalFileName(name)
        .storageKey(storageKey)
        .fileSize(file.getSize())
        .visible(visible)
        .build();
    return WikiAnalysisMaterialResponse.of(repository.save(material));
  }

  @Transactional
  public WikiAnalysisMaterialResponse update(Long id, String title, String description, String schoolName,
      Integer grade, Integer year, String semester, String examType, String subject, boolean visible,
      MultipartFile file) {
    WikiAnalysisMaterial material = getAdminEntity(id);
    material.updateMetadata(
        required(title, "제목"),
        blankToNull(description),
        blankToNull(schoolName),
        validGrade(grade),
        validYear(year),
        required(semester, "학기"),
        required(examType, "시험"),
        subjectOrDefault(subject),
        visible);
    if (file != null && !file.isEmpty()) {
      documentUploadValidator.validate(file);
      String name = originalFileName(file);
      String newStorageKey = saveFile(file, name);
      String oldStorageKey = material.replaceFile(name, newStorageKey, file.getSize(),
          DocumentUploadValidator.contentType(name));
      // 파일 삭제는 트랜잭션 결과에 묶는다 — 롤백되면 DB가 가리키는 기존 파일이 사라지면 안 된다.
      TransactionalFileDeletion.onReplace(
          () -> fileStorage.delete(oldStorageKey, RAW_FILE_TYPE),
          () -> fileStorage.delete(newStorageKey, RAW_FILE_TYPE));
    }
    return WikiAnalysisMaterialResponse.of(material);
  }

  @Transactional
  public WikiAnalysisMaterialResponse updateVisibility(Long id, boolean visible) {
    WikiAnalysisMaterial material = getAdminEntity(id);
    material.setVisible(visible);
    return WikiAnalysisMaterialResponse.of(material);
  }

  @Transactional
  public void softDelete(Long id) {
    getAdminEntity(id).softDelete();
  }

  @Transactional
  public void hardDelete(Long id) {
    WikiAnalysisMaterial material = getAdminEntity(id);
    String storageKey = material.getStorageKey();
    repository.delete(material);
    // 커밋된 뒤에만 파일을 지운다 — 삭제가 롤백되면 파일이 남아 DB와 어긋나지 않는다.
    TransactionalFileDeletion.afterCommit(() -> fileStorage.delete(storageKey, RAW_FILE_TYPE));
  }

  private WikiAnalysisMaterial getAdminEntity(Long id) {
    return repository.findAdminById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 자료를 찾을 수 없습니다."));
  }

  private String saveFile(MultipartFile file, String name) {
    try {
      return fileStorage.saveRaw(STORAGE_OWNER_ID, name, file.getInputStream(), file.getSize());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "분석 자료 파일 저장에 실패했습니다.", e);
    }
  }

  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename();
    return (filename == null || filename.isBlank()) ? "analysis" : filename;
  }

  private String subjectOrDefault(String subject) {
    String trimmed = blankToNull(subject);
    return trimmed == null ? DEFAULT_SUBJECT : trimmed;
  }

  private Integer validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~3(고1~고3) 사이 숫자로 입력해주세요.");
    }
    return grade;
  }

  private Integer validYear(Integer year) {
    if (year == null || year < 2000 || year > 2100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연도를 올바르게 입력해주세요.");
    }
    return year;
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
