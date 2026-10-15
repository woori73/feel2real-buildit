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
      "(사진|이미지|배너|배경|메인|히어로|교재|시간표)");

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

  private final AcademyImageService academyImageService;

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

    boolean hasExistingImage = checkExistingImage(principal, slotKey);
    String guideMessage = hasExistingImage
        ? "편집할 이미지를 찾았습니다. 미리보기를 생성합니다."
        : "이미지를 먼저 업로드한 후 편집할 수 있습니다.";

    return new ImageEditFlowDtos.ChatImageIntentResponse(
        true, slotKey, description, operations,
        hasExistingImage,
        guideMessage);
  }

  /** 해당 슬롯에 편집할 이미지가 있는지 확인한다. principal이 null이면(테스트 등) false. */
  private boolean checkExistingImage(JwtTokenProvider.AuthenticatedPrincipal principal, String slotKey) {
    if (principal == null) {
      return false;
    }
    try {
      return academyImageService.hasSourceImage(principal, slotKey);
    } catch (Exception e) {
      log.warn("기존 이미지 조회 실패: slot={}, error={}", slotKey, e.getMessage());
      return false;
    }
  }

  /**
   * 이미지 편집 실행 (사용자 확인 후).
   *
   * <p>AcademyImageService를 통해 PhotoCraft로 실제 편집을 수행하고,
   * 결과를 승인 플로우에 등록한다.
   *
   * @param principal 인증 사용자
   * @param slotKey 이미지 슬롯 (HERO_BANNER, SECTION_BG 등)
   * @param operations 편집 작업 목록 [{op, params}]
   * @return 미리보기 데이터 (원본 vs 수정본)
   */
  public ImageEditFlowDtos.ImagePreviewData executeEdit(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String slotKey,
      List<Map<String, Object>> operations) {

    log.info("이미지 편집 실행: slot={}, operations={}", slotKey, operations.size());

    // 1. Map 형태의 operations를 AcademyImageDtos.EditOperation으로 변환
    List<AcademyImageDtos.EditOperation> editOps = new ArrayList<>();
    for (Map<String, Object> opMap : operations) {
      String op = (String) opMap.get("op");
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) opMap.get("params");
      if (op == null) {
        // 중첩 구조가 아닌 평탄 구조인 경우: {op, params} 또는 {op, value}
        continue;
      }
      if (params == null) {
        params = new HashMap<>();
        // {op: "brightness", value: 20} 형태 지원
        if (opMap.containsKey("value")) {
          params.put("value", opMap.get("value"));
        }
      }
      editOps.add(new AcademyImageDtos.EditOperation(op, params));
    }

    // 평탄 구조 재시도: operations가 [{op, value}] 형태인 경우
    if (editOps.isEmpty()) {
      for (Map<String, Object> opMap : operations) {
        String op = (String) opMap.get("op");
        if (op != null) {
          Map<String, Object> params = new HashMap<>();
          for (Map.Entry<String, Object> e : opMap.entrySet()) {
            if (!"op".equals(e.getKey())) {
              params.put(e.getKey(), e.getValue());
            }
          }
          // value 키가 없으면 params 자체를 value로
          if (!params.containsKey("value") && params.containsKey("params")) {
            Object p = params.remove("params");
            if (p instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> pm = (Map<String, Object>) p;
              params.putAll(pm);
            }
          }
          editOps.add(new AcademyImageDtos.EditOperation(op, params));
        }
      }
    }

    if (editOps.isEmpty()) {
      throw new IllegalArgumentException("편집 작업이 비어 있습니다.");
    }

    // 3. AcademyImageService를 통해 실제 편집 수행
    AcademyImageDtos.EditImageRequest request =
        new AcademyImageDtos.EditImageRequest(
            null, // assetId=null: 슬롯의 최신 버전 사용
            slotKey,
            editOps,
            true); // applyToPreview=true: 미리보기에 스테이징

    AcademyImageDtos.EditImageResponse response =
        academyImageService.editImage(principal, request);

    log.info("이미지 편집 완료: assetId={}, version={}, desc={}",
        response.assetId(), response.version(), response.editDescription());

    // 5. 미리보기 데이터 반환 (원본 vs 수정본)
    return new ImageEditFlowDtos.ImagePreviewData(
        response.assetId(),
        slotKey,
        response.version(),
        AcademyImageService.previewUrl(response.assetId(), "original"),
        response.imageUrl(),
        response.editDescription(),
        response.stagedForPreview());
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

  /** 메시지에서 HeroMediaSlot 키를 추론한다 (기본: 원장 대표사진). */
  private String inferSlotKey(String message) {
    if (message.contains("시간표")) {
      return "schedule";
    }
    if (message.contains("교재")) {
      java.util.regex.Matcher m = Pattern.compile("([123])").matcher(message);
      return "textbook" + (m.find() ? m.group(1) : "1");
    }
    return "principal";
  }
}
