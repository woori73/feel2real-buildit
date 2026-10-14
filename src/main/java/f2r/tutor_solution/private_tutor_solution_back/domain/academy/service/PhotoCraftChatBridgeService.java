package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 채팅과 PhotoCraft 이미지 편집을 연결하는 브릿지.
 *
 * <p>기존 AcademyAiChatController를 수정하지 않고 동작한다.
 * 프론트엔드에서 채팅 메시지 전송 전 이 브릿지를 먼저 호출하여
 * 이미지 편집 의도이면 이미지 플로우로 분기하고,
 * 아니면 기존 AI 채팅으로 전달한다.
 *
 * <p>의도 감지 로직은 자체 포함 (PR #662의 AcademyImageIntentDetector와
 * 동일한 패턴, 독립적으로 동작).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCraftChatBridgeService {

  // 이미지 대상 키워드
  private static final Pattern IMAGE_TARGET = Pattern.compile(
      "(사진|이미지|배너|배경|메인|히어로)");

  // 밝기 관련
  private static final Pattern BRIGHTEN = Pattern.compile(
      "(밝게|밝은|밝기.*(높|올|증가)|환하게|화사하게)");
  private static final Pattern DARKEN = Pattern.compile(
      "(어둡게|어두운|밝기.*(낮|내|감소)|침침하게)");

  // 채도 관련
  private static final Pattern VIBRANT = Pattern.compile(
      "(선명하게|생생하게|채도.*(높|올)|색감.*(좋|선명))");

  // 노출 관련
  private static final Pattern EXPOSURE_UP = Pattern.compile("(노출.*(높|올|증가))");
  private static final Pattern EXPOSURE_DOWN = Pattern.compile("(노출.*(낮|내|감소))");

  private final ImageEditApprovalService approvalService;

  /**
   * 채팅 메시지에서 이미지 편집 의도를 감지.
   *
   * <p>프론트엔드 플로우:
   * <ol>
   *   <li>사용자가 채팅 메시지 입력</li>
   *   <li>프론트엔드가 POST /api/tutor/academy/images/flow/detect 호출</li>
   *   <li>imageEditDetected=true이면 이미지 편집 UI 표시</li>
   *   <li>false이면 기존 AI 채팅으로 메시지 전달</li>
   * </ol>
   */
  public ImageEditFlowDtos.ChatImageIntentResponse detectImageIntent(
      JwtTokenProvider.AuthenticatedPrincipal principal, String message) {

    if (message == null || message.isBlank()) {
      return noIntent();
    }

    // 이미지 대상이 언급되었는지 확인
    if (!IMAGE_TARGET.matcher(message).find()) {
      return noIntent();
    }

    List<Map<String, Object>> operations = new ArrayList<>();
    StringBuilder descBuilder = new StringBuilder();

    // 밝기
    if (BRIGHTEN.matcher(message).find()) {
      int value = extractIntensity(message, 20);
      operations.add(createOperation("brightness", value));
      appendDesc(descBuilder, "밝기 +" + value);
    } else if (DARKEN.matcher(message).find()) {
      int value = -extractIntensity(message, 20);
      operations.add(createOperation("brightness", value));
      appendDesc(descBuilder, "밝기 " + value);
    }

    // 채도
    if (VIBRANT.matcher(message).find()) {
      operations.add(createOperation("vibrance", 20));
      appendDesc(descBuilder, "채도 +20");
    }

    // 노출
    if (EXPOSURE_UP.matcher(message).find()) {
      operations.add(createOperation("exposure", 1.0));
      appendDesc(descBuilder, "노출 +1.0");
    } else if (EXPOSURE_DOWN.matcher(message).find()) {
      operations.add(createOperation("exposure", -1.0));
      appendDesc(descBuilder, "노출 -1.0");
    }

    if (operations.isEmpty()) {
      return noIntent();
    }

    String slotKey = inferSlotKey(message);
    String description = descBuilder.toString();

    log.info("이미지 편집 의도 감지: slot={}, desc={}", slotKey, description);

    return new ImageEditFlowDtos.ChatImageIntentResponse(
        true, slotKey, description, operations,
        false, // hasExistingImage: #662 병합 후 실제 조회
        "이미지를 먼저 업로드한 후 편집할 수 있습니다.");
  }

  /**
   * 이미지 편집 실행 (사용자 확인 후).
   *
   * <p>PR #662의 AcademyImageService와 연동 예정.
   * 현재는 승인 서비스에 등록하는 구조를 제공한다.
   */
  public ImageEditFlowDtos.ImagePreviewData executeEdit(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String slotKey,
      List<Map<String, Object>> operations) {

    // TODO(#662): AcademyImageService.editImage 호출
    // 현재는 플로우 구조만 제공
    log.info("이미지 편집 실행 요청: slot={}, operations={}", slotKey, operations.size());

    throw new UnsupportedOperationException(
        "PR #662 병합 후 구현: PhotoCraftImageService 연동 필요. " +
        "현재는 의도 감지(detect)까지만 동작합니다.");
  }

  private ImageEditFlowDtos.ChatImageIntentResponse noIntent() {
    return new ImageEditFlowDtos.ChatImageIntentResponse(
        false, null, null, List.of(), false, null);
  }

  private Map<String, Object> createOperation(String op, Object value) {
    Map<String, Object> params = new HashMap<>();
    params.put("value", value);
    Map<String, Object> operation = new HashMap<>();
    operation.put("op", op);
    operation.put("params", params);
    return operation;
  }

  private void appendDesc(StringBuilder sb, String desc) {
    if (sb.length() > 0) sb.append(", ");
    sb.append(desc);
  }

  private int extractIntensity(String message, int defaultValue) {
    Pattern numPattern = Pattern.compile("(\\d+)");
    var m = numPattern.matcher(message);
    if (m.find()) {
      try {
        int v = Integer.parseInt(m.group(1));
        return Math.max(5, Math.min(50, v));
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    return defaultValue;
  }

  private String inferSlotKey(String message) {
    if (message.contains("배너") || message.contains("메인") || message.contains("히어로")) {
      return "HERO_BANNER";
    }
    if (message.contains("배경")) {
      return "SECTION_BG";
    }
    return "HERO_BANNER";
  }
}
