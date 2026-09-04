package f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.dto.WikiLearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilearning.service.WikiLearningMaterialService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.web.FileDownloadResponses;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공개 BuildIt Wiki "모의고사 학습 자료" API.
 *
 * <p>권한(전부 서버 시큐리티가 강제 — frontend 표시와 무관):
 * <ul>
 *   <li>{@code GET /api/wiki/learning-materials} — 목록/제목: 비회원 포함 공개(SecurityConfig permitAll).
 *   <li>{@code POST /api/wiki/learning-materials} — 업로드: 회원(로그인) 전용({@code /api/**} 인증 게이트).
 *       uploader 는 {@code @AuthenticationPrincipal} 로만 기록한다.
 *   <li>{@code GET /api/wiki/learning-materials/{id}/download} — 다운로드: 회원 전용({@code /api/**} 인증 게이트).
 *       URL 직접 입력해도 anonymous 는 401.
 *   <li>{@code GET/DELETE /api/admin/wiki/learning-materials} — 관리: ADMIN 전용({@code /api/admin/**}).
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class WikiLearningMaterialController {

  private final WikiLearningMaterialService service;

  @GetMapping("/api/wiki/learning-materials")
  public ResponseEntity<List<WikiLearningMaterialResponse>> listPublic(
      @RequestParam(required = false) Integer grade) {
    return ResponseEntity.ok(service.searchPublic(grade));
  }

  @PostMapping(value = "/api/wiki/learning-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<WikiLearningMaterialResponse> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam String dataType,
      @RequestParam Integer grade,
      @RequestParam(required = false) String title,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(service.upload(principal.userId(), dataType, grade, title, file));
  }

  @GetMapping("/api/wiki/learning-materials/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    DownloadFile file = service.getDownload(id);
    return FileDownloadResponses.attachment(file.bytes(), file.contentType(), file.fileName());
  }

  @GetMapping("/api/admin/wiki/learning-materials")
  public ResponseEntity<List<WikiLearningMaterialResponse>> listAdmin() {
    return ResponseEntity.ok(service.listAdmin());
  }

  @DeleteMapping("/api/admin/wiki/learning-materials/{id}")
  public ResponseEntity<Void> adminDelete(@PathVariable Long id) {
    service.adminHardDelete(id);
    return ResponseEntity.noContent().build();
  }
}
