package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.ImageEditApprovalService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.PhotoCraftChatBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PhotoCraft 이미지 편집 플로우 API.
 *
 * <p>AI 채팅 → 이미지 편집 → 미리보기 → 승인 → 홈페이지 반영 흐름.
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>POST /api/tutor/academy/images/flow/detect - 채팅 메시지에서 이미지 의도 감지</li>
 *   <li>POST /api/tutor/academy/images/flow/edit - 이미지 편집 실행 (미리보기 스테이징)</li>
 *   <li>POST /api/tutor/academy/images/flow/approve - 승인/거부</li>
 *   <li>GET /api/tutor/academy/images/flow/{assetId}/status - 플로우 상태</li>
 * </ul>
 *
 * <p>권한: TUTOR (학원별 격리).
 * #662의 AcademyImageController와 충돌 없음 (다른 경로).
 */
@RestController
@RequestMapping("/api/tutor/academy/images/flow")
@RequiredArgsConstructor
public class ImageEditFlowController {

  private final PhotoCraftChatBridgeService bridgeService;
  private final ImageEditApprovalService approvalService;

  /**
   * 채팅 메시지에서 이미지 편집 의도 감지.
   *
   * <p>프론트엔드에서 AI 채팅 메시지 전송 전 호출.
   * imageEditDetected=true이면 이미지 편집 UI로 분기.
   */
  @PostMapping("/detect")
  public ResponseEntity<ImageEditFlowDtos.ChatImageIntentResponse> detectIntent(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, String> body) {
    String message = body.get("message");
    return ResponseEntity.ok(bridgeService.detectImageIntent(principal, message));
  }

  /**
   * 이미지 편집 실행.
   *
   * <p>사용자가 편집 내용을 확인하고 "미리보기"를 누르면 호출.
   * 편집 결과를 미리보기에 스테이징한다.
   */
  @PostMapping("/edit")
  public ResponseEntity<ImageEditFlowDtos.ImagePreviewData> executeEdit(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> body) {
    String slotKey = (String) body.get("slotKey");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("operations");
    return ResponseEntity.ok(bridgeService.executeEdit(principal, slotKey, operations));
  }

  /**
   * 승인/거부.
   *
   * <p>사용자가 원본 vs 수정본을 비교하고 승인/거부/재수정을 선택.
   */
  @PostMapping("/approve")
  public ResponseEntity<ImageEditFlowDtos.ApproveImageResponse> approve(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ImageEditFlowDtos.ApproveImageRequest request) {
    return ResponseEntity.ok(approvalService.approve(principal, request));
  }

  /**
   * WorkingCopy 현재 revision 조회.
   *
   * <p>승인된 이미지를 스테이징하기 전에 호출하여
   * 서버와 일치하는 revision을 확보한다.
   */
  @GetMapping("/working-copy-revision")
  public ResponseEntity<Map<String, Long>> getWorkingCopyRevision(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    long revision = approvalService.getCurrentRevision(principal);
    return ResponseEntity.ok(Map.of("revision", revision));
  }

  /**
   * 플로우 상태 조회.
   */
  @GetMapping("/{assetId}/status")
  public ResponseEntity<ImageEditFlowDtos.ImageEditFlowStatus> getStatus(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long assetId) {
    return ResponseEntity.ok(approvalService.getFlowStatus(principal, assetId));
  }
}
