package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 채팅과 PhotoCraft 이미지 편집을 연결하는 브릿지.
 *
 * <p>기존 AcademyAiChatController를 수정하지 않고 동작한다.
 * 프론트엔드에서 채팅 메시지 전송 전 이 브릿지를 먼저 호출하여
 * 이미지 편집 의도이면 이미지 플로우로 분기하고,
 * 아니면 기존 AI 채팅으로 전달한다.
 *
 * <p>의존성: #662의 AcademyImageIntentDetector, AcademyImageService
 * (PR #662 병합 후 사용 가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCraftChatBridgeService {

  // TODO(#662): PR #662 병합 후 주입 활성화
  // private final AcademyImageIntentDetector intentDetector;
  // private final AcademyImageService imageService;
  // private final AcademyImageAssetRepository assetRepository;

  /**
   * 채팅 메시지에서 이미지 편집 의도를 감지.
   *
   * <p>프론트엔드 플로우:
   * <ol>
   *   <li>사용자가 채팅 메시지 입력</li>
   *   <li>프론트엔드가 POST /api/tutor/academy/images/chat-detect 호출</li>
   *   <li>imageEditDetected=true이면 이미지 편집 UI 표시</li>
   *   <li>false이면 기존 AI 채팅으로 메시지 전달</li>
   * </ol>
   *
   * @param principal 인증 사용자
   * @param message 채팅 메시지
   * @return 감지 결과
   */
  public ImageEditFlowDtos.ChatImageIntentResponse detectImageIntent(
      JwtTokenProvider.AuthenticatedPrincipal principal, String message) {

    // TODO(#662): 실제 구현은 AcademyImageIntentDetector 사용
    // 현재는 기본 구조만 제공하고 #662 병합 후 연결
    log.info("이미지 의도 감지 요청 (stub): {}", message != null ? message.substring(0, Math.min(50, message.length())) : "null");

    // Stub: #662 병합 전까지는 감지하지 않음
    return new ImageEditFlowDtos.ChatImageIntentResponse(
        false, null, null, List.of(), false,
        "이미지 편집 기능은 준비 중입니다.");
  }

  /**
   * 이미지 편집 실행 (사용자 확인 후).
   *
   * <p>프론트엔드에서 사용자가 편집 내용을 확인하고 "적용"을 누르면 호출.
   *
   * @param principal 인증 사용자
   * @param slotKey 이미지 슬롯
   * @param operations 편집 작업
   * @return 미리보기 데이터
   */
  public ImageEditFlowDtos.ImagePreviewData executeEdit(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String slotKey,
      List<Map<String, Object>> operations) {

    // TODO(#662): AcademyImageService.editImage 호출
    // 1. AcademyImageDtos.EditImageRequest 구성
    // 2. imageService.editImage(principal, request) 호출
    // 3. 결과를 ImagePreviewData로 변환
    // 4. applyToPreview=true로 미리보기에 스테이징

    throw new UnsupportedOperationException(
        "PR #662 병합 후 구현 예정: AcademyImageService 연동 필요");
  }
}
