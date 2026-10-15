package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.MaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadedFileIngestor;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemGeneratorMaterialService {
  private static final int MAX_FILES_PER_UPLOAD = 20;
  private static final int TITLE_MAX = 150;

  private final ProblemGeneratorMaterialRepository materialRepository;
  private final UserRepository userRepository;
  private final UploadedFileIngestor uploadedFileIngestor;
  private final FileStorage fileStorage;

  @Transactional
  public List<MaterialResponse> uploadPrivate(JwtTokenProvider.AuthenticatedPrincipal principal,
      List<MultipartFile> files, String title) {
    return upload(principal, files, title, MaterialVisibility.PRIVATE);
  }

  @Transactional
  public List<MaterialResponse> uploadAdminPublic(JwtTokenProvider.AuthenticatedPrincipal principal,
      List<MultipartFile> files, String title) {
    requireAdmin(principal);
    return upload(principal, files, title, MaterialVisibility.PUBLIC);
  }

  @Transactional
  public List<MaterialResponse> upload(JwtTokenProvider.AuthenticatedPrincipal principal,
      List<MultipartFile> files, String title, MaterialVisibility visibility) {
    validateFiles(files);
    String normalizedTitle = requireTitle(title);
    User uploader = userRepository.findById(principal.userId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "업로더를 찾을 수 없습니다."));
    if (visibility == MaterialVisibility.PUBLIC && uploader.getRole() != Role.ADMIN) {
      throw new AccessDeniedException("공용 자료 등록은 관리자만 가능합니다.");
    }
    return files.stream().map(file -> {
      UploadedFileIngestor.StoredFile stored = uploadedFileIngestor.store(file, uploader.getUserId());
      ProblemGeneratorMaterial material = materialRepository.save(ProblemGeneratorMaterial.builder()
          .uploadedBy(uploader)
          .title(derivedTitle(normalizedTitle, file.getOriginalFilename(), files.size()))
          .originalFileName(safeOriginalName(file.getOriginalFilename()))
          .storageKey(stored.storageKey())
          .fileType(stored.fileType())
          .fileSize(stored.fileSize())
          .visibility(visibility)
          .build());
      return MaterialResponse.of(material, pageCountOf(material));
    }).toList();
  }

  public Page<MaterialResponse> list(JwtTokenProvider.AuthenticatedPrincipal principal, Pageable pageable) {
    return materialRepository.findAccessible(principal.userId(), pageable)
        .map(material -> MaterialResponse.of(material, pageCountOf(material)));
  }

  public ProblemGeneratorMaterial loadAccessible(JwtTokenProvider.AuthenticatedPrincipal principal, Long materialId) {
    ProblemGeneratorMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
    if (material.isPublic() || material.isOwnedBy(principal.userId()) || isAdmin(principal)) return material;
    throw new AccessDeniedException("접근할 수 없는 자료입니다.");
  }

  public byte[] readFile(JwtTokenProvider.AuthenticatedPrincipal principal, Long materialId, int page) {
    ProblemGeneratorMaterial material = loadAccessible(principal, materialId);
    try {
      return fileStorage.readPage(material.getStorageKey(), material.getFileType(), page);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 파일을 읽을 수 없습니다.");
    }
  }

  @Transactional
  public void delete(JwtTokenProvider.AuthenticatedPrincipal principal, Long materialId) {
    ProblemGeneratorMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
    if (!material.isOwnedBy(principal.userId()) && !isAdmin(principal)) {
      throw new AccessDeniedException("자기 자료만 삭제할 수 있습니다.");
    }
    String storageKey = material.getStorageKey();
    String fileType = material.getFileType();
    materialRepository.delete(material);
    fileStorage.deleteReported(storageKey, fileType);
  }

  @Transactional
  public MaterialResponse updateVisibility(JwtTokenProvider.AuthenticatedPrincipal principal,
      Long materialId, MaterialVisibility visibility) {
    requireAdmin(principal);
    ProblemGeneratorMaterial material = materialRepository.findById(materialId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
    material.updateVisibility(visibility);
    return MaterialResponse.of(material, pageCountOf(material));
  }

  private int pageCountOf(ProblemGeneratorMaterial material) {
    return fileStorage.pageCount(material.getStorageKey(), material.getFileType());
  }

  private void validateFiles(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) throw new IllegalArgumentException("업로드할 이미지 파일이 필요합니다.");
    if (files.size() > MAX_FILES_PER_UPLOAD) throw new IllegalArgumentException("한 번에 최대 20개 파일만 업로드할 수 있습니다.");
    files.forEach(file -> {
      if (file == null || file.isEmpty()) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
      String contentType = file.getContentType();
      if (contentType == null || !contentType.startsWith("image/")) {
        throw new IllegalArgumentException("Digital Twin 자료는 이미지 파일만 업로드할 수 있습니다.");
      }
    });
  }

  private String requireTitle(String title) {
    if (title == null || title.isBlank()) throw new IllegalArgumentException("자료 제목을 입력해 주세요.");
    String normalized = title.trim();
    if (normalized.length() > TITLE_MAX) throw new IllegalArgumentException("자료 제목은 150자를 넘을 수 없습니다.");
    return normalized;
  }

  private String derivedTitle(String title, String filename, int fileCount) {
    if (fileCount <= 1) return title;
    return truncate(title + " · " + safeOriginalName(filename));
  }

  private String truncate(String value) { return value.length() <= TITLE_MAX ? value : value.substring(0, TITLE_MAX); }
  private String safeOriginalName(String filename) {
    if (filename == null || filename.isBlank()) return "upload";
    String normalized = filename.replace('\\', '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1);
  }
  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }
  private void requireAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!isAdmin(principal)) throw new AccessDeniedException("관리자 권한이 필요합니다.");
  }
}
