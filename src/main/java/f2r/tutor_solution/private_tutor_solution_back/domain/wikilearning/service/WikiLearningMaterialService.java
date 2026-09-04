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
    this.repository = repository; this.userRepository = userRepository; this.fileStorage = fileStorage;
    this.documentUploadValidator = documentUploadValidator; this.maxDocumentBytes = maxDocumentBytes;
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
  @Transactional(readOnly = true)
  public WikiLearningMaterialResponse getDetail(Long id) { return WikiLearningMaterialResponse.of(getActive(id)); }

  /** Public inline preview bytes. Only PDF and validated HTML are previewable. */
  @Transactional(readOnly = true)
  public DownloadFile getPreview(Long id) {
    WikiLearningMaterial material = getActive(id);
    if (!WikiLearningMaterialResponse.isPreviewableContentType(material.getContentType())) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "이 형식은 미리보기를 지원하지 않습니다.");
    }
    return readFile(material);
  }

  @Transactional(readOnly = true)
  public DownloadFile getDownload(Long id) { return readFile(getActive(id)); }

  private WikiLearningMaterial getActive(Long id) {
    return repository.findActiveById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료를 찾을 수 없습니다."));
  }
  private DownloadFile readFile(WikiLearningMaterial material) {
    try {
      byte[] bytes = fileStorage.readSingleLimited(material.getStorageKey(), RAW_FILE_TYPE, maxDocumentBytes);
      return new DownloadFile(bytes, material.getOriginalFileName(), material.getContentType());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료 파일을 찾을 수 없습니다.", e);
    }
  }

  @Transactional
  public WikiLearningMaterialResponse upload(Long uploaderUserId, String dataType, Integer grade,
      String title, MultipartFile file) {
    User uploader = userRepository.findById(uploaderUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    WikiLearningMaterialType type = WikiLearningMaterialType.parse(dataType);
    Integer validGrade = validGrade(grade);
    documentUploadValidator.validate(file);
    String fileName = originalFileName(file);
    String resolvedTitle = resolveTitle(title, fileName);
    String storageKey = saveFile(file, fileName);
    WikiLearningMaterial material = WikiLearningMaterial.builder()
        .dataType(type).grade(validGrade).title(resolvedTitle)
        .contentType(DocumentUploadValidator.contentType(fileName)).originalFileName(fileName)
        .storageKey(storageKey).fileSize(file.getSize()).uploaderUserId(uploader.getUserId())
        .uploaderName(uploader.getDisplayName()).build();
    return WikiLearningMaterialResponse.of(repository.save(material));
  }

  @Transactional
  public void adminHardDelete(Long id) {
    WikiLearningMaterial material = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 자료를 찾을 수 없습니다."));
    String storageKey = material.getStorageKey(); repository.delete(material); fileStorage.delete(storageKey, RAW_FILE_TYPE);
  }
  private String saveFile(MultipartFile file, String name) {
    try { return fileStorage.saveRaw(STORAGE_OWNER_ID, name, file.getInputStream(), file.getSize()); }
    catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "학습 자료 파일 저장에 실패했습니다.", e); }
  }
  private String originalFileName(MultipartFile file) {
    String filename = file.getOriginalFilename(); return (filename == null || filename.isBlank()) ? "material" : filename;
  }
  private String resolveTitle(String title, String fileName) {
    String trimmed = title == null ? "" : title.trim(); if (trimmed.isEmpty()) trimmed = titleFromFileName(fileName);
    if (trimmed.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해주세요.");
    return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
  }
  private String titleFromFileName(String fileName) {
    String base = fileName; int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
    if (slash >= 0) base = base.substring(slash + 1); int dot = base.lastIndexOf('.'); if (dot > 0) base = base.substring(0, dot);
    return base.replace('_', ' ').replaceAll("\\s+", " ").trim();
  }
  private Integer validGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년은 1~3(고1~고3) 사이 숫자로 입력해주세요.");
    return grade;
  }
}
