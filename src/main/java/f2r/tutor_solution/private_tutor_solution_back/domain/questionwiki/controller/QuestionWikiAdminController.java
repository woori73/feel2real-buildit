package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.LinkableMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiMaterialLinkRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiMaterialLinkResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service.QuestionWikiService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문항 학습자료 관리(ADMIN 전용). {@code /api/admin/**} → Spring Security {@code hasRole('ADMIN')}.
 * 서비스에서도 principal 기준으로 한 번 더 검증한다.
 *
 * <ul>
 *   <li>{@code GET /materials/search} — "Wiki 자료에서 찾기": 기존 자료실에서 연결 후보 검색
 *   <li>{@code POST /{id}/material-links} — 검색해서 고른 자료를 문항에 연결
 *   <li>{@code POST /{id}/materials/upload} — "직접 올리기": 파일을 Wiki 학습자료로 저장 + 연결
 *   <li>{@code DELETE /material-links/{linkId}} — 연결 해제
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/wiki/questions")
public class QuestionWikiAdminController {

  private final QuestionWikiService questionWikiService;

  @GetMapping("/materials/search")
  public ResponseEntity<List<LinkableMaterialResponse>> searchMaterials(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) String q) {
    return ResponseEntity.ok(questionWikiService.searchLinkableMaterials(principal, q));
  }

  @PostMapping("/{questionWikiId}/material-links")
  public ResponseEntity<QuestionWikiMaterialLinkResponse> addMaterialLink(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId,
      @RequestBody QuestionWikiMaterialLinkRequest request) {
    return ResponseEntity.ok(questionWikiService.addMaterialLink(principal, questionWikiId, request));
  }

  @PostMapping(value = "/{questionWikiId}/materials/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<QuestionWikiMaterialLinkResponse> uploadMaterial(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long questionWikiId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String docLabel,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(questionWikiService.uploadAndLinkMaterial(
        principal, questionWikiId, title, description, docLabel, file));
  }

  @DeleteMapping("/material-links/{linkId}")
  public ResponseEntity<Void> removeMaterialLink(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long linkId) {
    questionWikiService.removeMaterialLink(principal, linkId);
    return ResponseEntity.noContent().build();
  }
}
