package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * P1 AI 홈페이지 제작 엔진 - 자연어 요구사항 분석.
 *
 * <p>사용자의 자연어 요청을 구조화된 요구사항으로 분석한다:
 * <ul>
 *   <li>지역 (예: 압구정, 강남)
 *   <li>대상 (예: 중학생, 고등학생)
 *   <li>과목/분야 (예: 영어, 수학, 예체능)
 *   <li>강점 (예: 내신 대비, 입시)
 *   <li>강조점 (예: 학부모 상담, 1:1 관리)
 *   <li>분위기/톤 (예: 깔끔하고 신뢰감 있게)
 * </ul>
 *
 * <p>허위 정보 방지:
 * <ul>
 *   <li>요청에 없는 경력·성적·합격 실적은 절대 생성하지 않는다.
 *   <li>구체적 숫자(년수, 점수, 인원)는 요청에 명시된 경우만 사용한다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcademyRequirementAnalyzer {

  /** 분석된 요구사항. */
  public record AcademyRequirements(
      String location,
      String target,
      String subject,
      String preset,
      List<String> strengths,
      List<String> emphases,
      String tone,
      Map<String, String> explicitValues) {

    public boolean hasLocation() {
      return location != null && !location.isBlank();
    }

    public boolean hasStrengths() {
      return strengths != null && !strengths.isEmpty();
    }
  }

  /** 과목 키워드 → 프리셋 매핑. */
  private static final Map<String, String> SUBJECT_PRESET_MAP = Map.ofEntries(
      Map.entry("영어", "ENGLISH"),
      Map.entry("영어회화", "ENGLISH"),
      Map.entry("회화", "ENGLISH"),
      Map.entry("수학", "EXAM"),
      Map.entry("국어", "EXAM"),
      Map.entry("과학", "EXAM"),
      Map.entry("사회", "EXAM"),
      Map.entry("입시", "EXAM"),
      Map.entry("내신", "EXAM"),
      Map.entry("수능", "EXAM"),
      Map.entry("미술", "ARTS"),
      Map.entry("음악", "ARTS"),
      Map.entry("예체능", "ARTS"),
      Map.entry("체육", "ARTS"),
      Map.entry("댄스", "ARTS"),
      Map.entry("공부방", "STUDY"),
      Map.entry("독서실", "STUDY"),
      Map.entry("온라인", "ONLINE"),
      Map.entry("인강", "ONLINE"));

  /**
   * 분위기/스타일 키워드 → 신규 5종 디자인(MINIMAL·DARK·WARM·PHOTO·DYNAMIC).
   * 과목 키워드와 분리한다: 스타일 단어("따뜻한")가 과목(subject)으로 잡혀 소개 문구·과목 조회를 오염시키지 않게 하고,
   * 스타일 키워드가 있으면 업종 기본 프리셋보다 우선한다(운영자가 말한 취향이 업종 추정보다 강한 신호).
   */
  private static final Map<String, String> STYLE_PRESET_MAP = Map.ofEntries(
      Map.entry("미니멀", "MINIMAL"),
      Map.entry("심플", "MINIMAL"),
      Map.entry("깔끔", "MINIMAL"),
      Map.entry("여백", "MINIMAL"),
      Map.entry("다크", "DARK"),
      Map.entry("다크모드", "DARK"),
      Map.entry("어두운", "DARK"),
      Map.entry("시크", "DARK"),
      Map.entry("감성", "WARM"),
      Map.entry("따뜻한", "WARM"),
      Map.entry("아늑", "WARM"),
      Map.entry("포근", "WARM"),
      Map.entry("스토리", "WARM"),
      Map.entry("포토", "PHOTO"),
      Map.entry("사진", "PHOTO"),
      Map.entry("비주얼", "PHOTO"),
      Map.entry("갤러리", "PHOTO"),
      Map.entry("이미지", "PHOTO"),
      Map.entry("다이나믹", "DYNAMIC"),
      Map.entry("역동", "DYNAMIC"),
      Map.entry("화려", "DYNAMIC"),
      Map.entry("생동감", "DYNAMIC"),
      Map.entry("애니메이션", "DYNAMIC"));

  /** 강점 키워드. */
  private static final List<String> STRENGTH_KEYWORDS = List.of(
      "내신 대비", "내신대비", "내신",
      "수능 대비", "수능대비", "수능",
      "입시", "1:1", "일대일", "소수정예",
      "맞춤", "개별", "관리형",
      "회화", "문법", "독해", "작문",
      "기초", "심화", "선행");

  /** 강조점 키워드. */
  private static final List<String> EMPHASIS_KEYWORDS = List.of(
      "학부모 상담", "상담",
      "무료 체험", "체험",
      "레벨 테스트", "테스트",
      "진단", "학습 관리", "성적 관리",
      "진로 상담", "입시 상담");

  /** 톤/분위기 키워드 → 스타일 지시. */
  private static final Map<String, String> TONE_MAP = Map.ofEntries(
      Map.entry("깔끔", "깔끔하고 정돈된"),
      Map.entry("신뢰감", "신뢰감 있는"),
      Map.entry("신뢰", "신뢰감 있는"),
      Map.entry("친근", "친근하고 따뜻한"),
      Map.entry("따뜻", "따뜻하고 친근한"),
      Map.entry("전문적", "전문적이고 권위 있는"),
      Map.entry("세련", "세련되고 모던한"),
      Map.entry("활기", "활기차고 역동적인"),
      Map.entry("차분", "차분하고 안정적인"));

  /** 지역 패턴 (시/구/동). */
  private static final Pattern LOCATION_PATTERN = Pattern.compile(
      "([가-힣]+(?:시|구|동|읍|면))");

  /** 명시적 숫자 값 패턴 (예: "15만원", "10년", "95점"). */
  private static final Pattern EXPLICIT_VALUE_PATTERN = Pattern.compile(
      "(\\d+[\\d,]*)(만원|원|년|점|명|%)");

  /**
   * 자연어 요청을 분석하여 구조화된 요구사항을 반환한다.
   */
  public AcademyRequirements analyze(String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("요청 내용을 입력해주세요.");
    }

    String location = extractLocation(message);
    String target = extractTarget(message);
    String subject = extractSubject(message);
    String preset = determinePreset(subject, message);
    List<String> strengths = extractStrengths(message);
    List<String> emphases = extractEmphases(message);
    String tone = extractTone(message);
    Map<String, String> explicitValues = extractExplicitValues(message);

    return new AcademyRequirements(
        location, target, subject, preset, strengths, emphases, tone, explicitValues);
  }

  private String extractLocation(String message) {
    // "압구정에 있는" 형태를 먼저 본다. 접미사 패턴을 먼저 쓰면 "압구정"이 "압구"로 잘린다.
    Matcher m2 = Pattern.compile("([가-힣]{2,})에\\s*(?:있는|위치)").matcher(message);
    if (m2.find()) {
      return m2.group(1);
    }
    Matcher m = LOCATION_PATTERN.matcher(message);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  private String extractTarget(String message) {
    String lower = message.toLowerCase();
    if (lower.contains("중학생") || lower.contains("중등")) return "중학생";
    if (lower.contains("고등학생") || lower.contains("고등")) return "고등학생";
    if (lower.contains("초등학생") || lower.contains("초등")) return "초등학생";
    if (lower.contains("성인")) return "성인";
    if (lower.contains("유아")) return "유아";
    return null;
  }

  private String extractSubject(String message) {
    String lower = message.toLowerCase();
    // Map.ofEntries 순회 순서는 JVM 실행마다 달라 비결정적이다. 메시지에서 가장 먼저 나온 키워드를 고른다.
    String best = null;
    int bestIdx = Integer.MAX_VALUE;
    for (String key : SUBJECT_PRESET_MAP.keySet()) {
      int idx = lower.indexOf(key.toLowerCase());
      if (idx < 0) {
        continue;
      }
      if (idx < bestIdx || (idx == bestIdx && best != null && key.length() > best.length())) {
        best = key;
        bestIdx = idx;
      }
    }
    return best;
  }

  /**
   * 프리셋 결정. 과목 키워드와 스타일 키워드를 한 줄로 놓고 메시지에서 가장 먼저 나온 것을 고른다
   * (기존 "선행 키워드 우선" 규칙 유지 — "깔끔하고 신뢰감 있게" 같은 일반 톤 표현이 뒤에 붙어도
   * 과목에서 정한 프리셋이 뒤집히지 않는다). 스타일 키워드는 subject로는 쓰이지 않는다.
   */
  private String determinePreset(String subject, String message) {
    String lower = message.toLowerCase();
    String best = null;
    String bestPreset = null;
    int bestIdx = Integer.MAX_VALUE;
    for (Map<String, String> map : List.of(SUBJECT_PRESET_MAP, STYLE_PRESET_MAP)) {
      for (Map.Entry<String, String> e : map.entrySet()) {
        int idx = lower.indexOf(e.getKey().toLowerCase());
        if (idx < 0) {
          continue;
        }
        if (idx < bestIdx || (idx == bestIdx && best != null && e.getKey().length() > best.length())) {
          best = e.getKey();
          bestPreset = e.getValue();
          bestIdx = idx;
        }
      }
    }
    return bestPreset == null ? "EXAM" : bestPreset; // 기본값
  }

  private List<String> extractStrengths(String message) {
    List<String> result = new ArrayList<>();
    String lower = message.toLowerCase();
    for (String keyword : STRENGTH_KEYWORDS) {
      if (lower.contains(keyword.toLowerCase()) && !result.contains(keyword)) {
        result.add(keyword);
      }
    }
    return result;
  }

  private List<String> extractEmphases(String message) {
    List<String> result = new ArrayList<>();
    String lower = message.toLowerCase();
    for (String keyword : EMPHASIS_KEYWORDS) {
      if (lower.contains(keyword.toLowerCase()) && !result.contains(keyword)) {
        result.add(keyword);
      }
    }
    return result;
  }

  /**
   * 톤/분위기 키워드를 결정적으로 추출한다.
   * 메시지 등장 순서대로 정렬하고, 같은 위치면 더 긴 키워드를 우선한다.
   */
  private String extractTone(String message) {
    String lower = message.toLowerCase();
    List<Map.Entry<String, String>> matched = new ArrayList<>();
    for (Map.Entry<String, String> entry : TONE_MAP.entrySet()) {
      if (lower.contains(entry.getKey().toLowerCase())) {
        matched.add(entry);
      }
    }
    matched.sort((a, b) -> {
      int ia = lower.indexOf(a.getKey().toLowerCase());
      int ib = lower.indexOf(b.getKey().toLowerCase());
      if (ia != ib) {
        return Integer.compare(ia, ib);
      }
      return Integer.compare(b.getKey().length(), a.getKey().length());
    });
    List<String> tones = new ArrayList<>();
    for (Map.Entry<String, String> entry : matched) {
      if (!tones.contains(entry.getValue())) {
        tones.add(entry.getValue());
      }
    }
    return tones.isEmpty() ? "전문적이고 신뢰감 있는" : String.join(", ", tones);
  }

  /**
   * 명시적으로 언급된 숫자 값을 추출한다.
   * 허위 생성을 방지하기 위해 요청에 있는 값만 사용한다.
   */
  private Map<String, String> extractExplicitValues(String message) {
    Map<String, String> values = new HashMap<>();
    Matcher m = EXPLICIT_VALUE_PATTERN.matcher(message);
    while (m.find()) {
      String num = m.group(1).replace(",", "");
      String unit = m.group(2);
      // 컨텍스트 기반 키 추론 (간단한 휴리스틱)
      String key = inferValueKey(message, m.start(), unit);
      if (key != null) {
        values.put(key, num + unit);
      }
    }
    return values;
  }

  private String inferValueKey(String message, int pos, String unit) {
    // 위치 앞뒤 20자 컨텍스트 확인
    int start = Math.max(0, pos - 20);
    int end = Math.min(message.length(), pos + 20);
    String context = message.substring(start, end).toLowerCase();

    if (unit.equals("만원") || unit.equals("원")) {
      if (context.contains("수강료") || context.contains("월")) return "monthlyTuition";
    }
    if (unit.equals("년")) {
      if (context.contains("경력") || context.contains("운영")) return "operationYears";
    }
    if (unit.equals("점") || unit.equals("%")) {
      if (context.contains("향상") || context.contains("성적")) return "scoreImprovement";
    }
    return null;
  }
}
