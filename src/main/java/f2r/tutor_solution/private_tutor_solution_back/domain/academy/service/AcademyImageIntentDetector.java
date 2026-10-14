package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyImageDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 채팅에서 이미지 편집 의도 감지.
 *
 * <p>기존 AcademyAiChatService를 수정하지 않고 독립 컴포넌트로 동작한다.
 * 채팅 컨트롤러에서 사용자 메시지 수신 시 이 감지기를 먼저 호출하여
 * 이미지 편집 요청이면 PhotoCraft 도구 호출 흐름으로 분기한다.
 *
 * <p>지원 표현:
 * <ul>
 *   <li>"메인 사진을 밝은 분위기로 바꿔줘" → brightness +20</li>
 *   <li>"밝기를 높여줘" → brightness +30</li>
 *   <li>"어둡게 해줘" → brightness -20</li>
 *   <li>"색감을 선명하게" → vibrance +20</li>
 *   <li>"노출을 올려줘" → exposure +1.0</li>
 * </ul>
 */
@Slf4j
@Service
public class AcademyImageIntentDetector {

  // 밝기 관련 패턴
  private static final Pattern BRIGHTEN = Pattern.compile(
      "(밝게|밝은|밝기.*(높|올|증가)|환하게|화사하게)");
  private static final Pattern DARKEN = Pattern.compile(
      "(어둡게|어두운|밝기.*(낮|내|감소)|침침하게)");

  // 채도 관련 패턴
  private static final Pattern VIBRANT = Pattern.compile(
      "(선명하게|생생하게|채도.*(높|올)|색감.*(좋|선명))");
  private static final Pattern MUTED = Pattern.compile(
      "(채도.*(낮|내)|색.*(은은|부드럽))");

  // 노출 관련 패턴
  private static final Pattern EXPOSURE_UP = Pattern.compile(
      "(노출.*(높|올|증가))");
  private static final Pattern EXPOSURE_DOWN = Pattern.compile(
      "(노출.*(낮|내|감소))");

  // 이미지 대상 패턴
  private static final Pattern IMAGE_TARGET = Pattern.compile(
      "(사진|이미지|배너|배경|메인|히어로)");

  /**
   * 이미지 편집 의도 감지.
   *
   * @param message 사용자 메시지
   * @return 감지된 의도 (없으면 empty)
   */
  public ImageEditIntent detect(String message) {
    if (message == null || message.isBlank()) {
      return ImageEditIntent.empty();
    }

    // 이미지 관련 대상이 언급되었는지 확인
    boolean hasImageTarget = IMAGE_TARGET.matcher(message).find();
    if (!hasImageTarget) {
      return ImageEditIntent.empty();
    }

    List<AcademyImageDtos.EditOperation> operations = new ArrayList<>();

    // 밝기
    if (BRIGHTEN.matcher(message).find()) {
      int value = extractIntensity(message, 20);
      operations.add(new AcademyImageDtos.EditOperation(
          "brightness", Map.of("value", value)));
    } else if (DARKEN.matcher(message).find()) {
      int value = -extractIntensity(message, 20);
      operations.add(new AcademyImageDtos.EditOperation(
          "brightness", Map.of("value", value)));
    }

    // 채도
    if (VIBRANT.matcher(message).find()) {
      operations.add(new AcademyImageDtos.EditOperation(
          "vibrance", Map.of("value", 20)));
    } else if (MUTED.matcher(message).find()) {
      operations.add(new AcademyImageDtos.EditOperation(
          "vibrance", Map.of("value", -20)));
    }

    // 노출
    if (EXPOSURE_UP.matcher(message).find()) {
      operations.add(new AcademyImageDtos.EditOperation(
          "exposure", Map.of("value", 1.0)));
    } else if (EXPOSURE_DOWN.matcher(message).find()) {
      operations.add(new AcademyImageDtos.EditOperation(
          "exposure", Map.of("value", -1.0)));
    }

    if (operations.isEmpty()) {
      return ImageEditIntent.empty();
    }

    // 슬롯 추론
    String slotKey = inferSlotKey(message);

    log.info("이미지 편집 의도 감지: slot={}, operations={}", slotKey, operations.size());
    return ImageEditIntent.of(slotKey, operations);
  }

  private int extractIntensity(String message, int defaultValue) {
    // "밝기를 30 올려줘" 같은 숫자 추출
    Pattern numPattern = Pattern.compile("(\\d+)");
    Matcher m = numPattern.matcher(message);
    if (m.find()) {
      try {
        int v = Integer.parseInt(m.group(1));
        return Math.max(5, Math.min(50, v)); // 5~50 범위로 제한
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
    // 기본값: 히어로 배너
    return "HERO_BANNER";
  }

  /** 이미지 편집 의도. */
  public record ImageEditIntent(
      boolean detected,
      String slotKey,
      List<AcademyImageDtos.EditOperation> operations) {

    public static ImageEditIntent empty() {
      return new ImageEditIntent(false, null, List.of());
    }

    public static ImageEditIntent of(String slotKey, List<AcademyImageDtos.EditOperation> operations) {
      return new ImageEditIntent(true, slotKey, operations);
    }
  }
}
