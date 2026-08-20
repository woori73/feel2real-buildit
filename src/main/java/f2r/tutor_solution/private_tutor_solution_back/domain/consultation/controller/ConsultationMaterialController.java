package f2r.tutor_solution.private_tutor_solution_back.domain.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.consultation.dto.ConsultationMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.consultation.service.ConsultationMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 상담 피드백 자료 — 강사/Admin 업로드 + 삭제.
 *
 * <p>조회는 conversation detail의 {@code sources}(sourceType=CONSULTATION 필터)를,
 * 썸네일/다운로드는 {@code GET /api/sources/{id}/file|download}를 재활용하므로 여기엔 없다.
 * 삭제는 IDOR 가드(매핑 부재/타 type/미존재) 위반 시 404를 반환한다(C1).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{conversationId}/consultation-materials")
public class ConsultationMaterialController {

  private final ConsultationMaterialService consultationMaterialService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<List<ConsultationMaterialResponse>> upload(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long conversationId,
      @RequestPart("files") List<MultipartFile> files) {
    return ResponseEntity.ok(consultationMaterialService.upload(principal, conversationId, files));
  }

  @DeleteMapping("/{sourceId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long conversationId,
      @PathVariable Long sourceId) {
    consultationMaterialService.delete(conversationId, sourceId);
    return ResponseEntity.noContent().build();
  }
}
