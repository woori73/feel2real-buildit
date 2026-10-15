package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.MaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.VisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.MaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.ProblemGeneratorMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ProblemGeneratorMaterialController {
  private final ProblemGeneratorMaterialService materialService;

  @PostMapping(value = "/api/problem-generator/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<MaterialResponse>> uploadPrivate(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("files") List<MultipartFile> files,
      @RequestParam String title) {
    List<MaterialResponse> saved = materialService.uploadPrivate(principal, files, title);
    URI location = saved.isEmpty() ? URI.create("/api/problem-generator/materials")
        : URI.create("/api/problem-generator/materials/" + saved.get(0).materialId());
    return ResponseEntity.created(location).body(saved);
  }

  @GetMapping("/api/problem-generator/materials")
  public ResponseEntity<Page<MaterialResponse>> list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(materialService.list(principal, pageable));
  }

  @GetMapping("/api/problem-generator/materials/{materialId}/file")
  public ResponseEntity<byte[]> file(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long materialId,
      @RequestParam(defaultValue = "1") int page) {
    ProblemGeneratorMaterial material = materialService.loadAccessible(principal, materialId);
    byte[] bytes = materialService.readFile(principal, materialId, page);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + material.getOriginalFileName() + "\"")
        .contentType(contentType(material))
        .body(bytes);
  }

  @DeleteMapping("/api/problem-generator/materials/{materialId}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long materialId) {
    materialService.delete(principal, materialId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/api/admin/problem-generator/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<MaterialResponse>> uploadPublic(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestPart("files") List<MultipartFile> files,
      @RequestParam String title) {
    List<MaterialResponse> saved = materialService.uploadAdminPublic(principal, files, title);
    URI location = saved.isEmpty() ? URI.create("/api/admin/problem-generator/materials")
        : URI.create("/api/problem-generator/materials/" + saved.get(0).materialId());
    return ResponseEntity.created(location).body(saved);
  }

  @PatchMapping("/api/admin/problem-generator/materials/{materialId}/visibility")
  public ResponseEntity<MaterialResponse> updateVisibility(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long materialId,
      @RequestBody VisibilityUpdateRequest request) {
    MaterialVisibility visibility = request != null ? request.visibility() : MaterialVisibility.PRIVATE;
    return ResponseEntity.ok(materialService.updateVisibility(principal, materialId, visibility));
  }

  private MediaType contentType(ProblemGeneratorMaterial material) {
    if ("PDF".equals(material.getFileType())) return MediaType.IMAGE_JPEG;
    if ("IMAGE".equals(material.getFileType())) {
      return MediaType.parseMediaType(ImageSanitizer.contentTypeForStorageKey(material.getStorageKey()));
    }
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
