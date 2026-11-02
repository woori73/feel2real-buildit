package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 문구 생성. 브랜드 프리셋 + 최근 주제 목록으로 LLM을 한 번 부르고(JSON), 검사에 걸리면 1회만 다시 만든다.
 * 고정 문구는 LLM에 맡기지 않고 서버가 정확히 붙인다. 이미지용 영어 프롬프트도 같은 호출에서 받는다
 * (규칙의 한국어 이미지 지시를 GPU 이미지 모델이 읽을 수 있게).
 */
@Service
public class SnsCaptionService {

  public static final int MAX_CAPTION_CHARS = 2200;
  public static final int MAX_HASHTAGS = 30;
  private static final int MAX_AI_HASHTAGS = 10;

  /** 본문에 새 가격·할인·비율·문항 수를 만들지 않게 막는다(고정 문구는 검사 대상 아님). */
  private static final Pattern PRICE_LIKE = Pattern.compile(
      "\\d[\\d,.]*\\s*(원|만\\s*원|%|퍼센트|문항)|할인");
  private static final Pattern HASHTAG = Pattern.compile("#?([\\p{L}\\p{N}_]{1,40})");

  public record Generated(String topic, String body, String caption, String hashtags, String imagePromptEn,
      String model, String prompt) {}

  public static class CaptionRejected extends RuntimeException {
    public CaptionRejected(String message) {
      super(message);
    }
  }

  private final NewStartLlmTransport transport;
  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  public SnsCaptionService(MetaMuseSparkLlmTransport transport) {
    this((NewStartLlmTransport) transport);
  }

  SnsCaptionService(NewStartLlmTransport transport) {
    this.transport = transport;
  }

  public String systemPrompt() {
    return "너는 인스타그램 피드 문구를 쓰는 한국어 카피라이터다. 과장되거나 광고처럼 들뜬 표현을 쓰지 않고, 사실이 아닌 수치·조건·일정을 지어내지 않는다. "
        + "반드시 JSON 객체 하나만 출력한다. 다른 설명이나 코드블록 표시는 쓰지 않는다.";
  }

  public String buildPrompt(SnsAutomationRule rule, SnsBrandPreset brand, List<String> recentTopics) {
    StringBuilder sb = new StringBuilder();
    sb.append("[브랜드] ").append(brand.getName()).append('\n');
    line(sb, "업종", brand.getIndustry());
    line(sb, "대상", brand.getAudience());
    line(sb, "목적", brand.getPurpose());
    line(sb, "상세 지시", brand.getInstructions());
    List<String> banned = brand.bannedPhraseList();
    if (!banned.isEmpty()) line(sb, "쓰면 안 되는 표현", String.join(", ", banned));
    if (brand.getFixedText() != null && !brand.getFixedText().isBlank()) {
      sb.append("- 고정 문구는 서버가 본문 ").append(brand.getFixedTextPosition() == SnsBrandPreset.FixedTextPosition.TOP ? "위" : "아래")
          .append("에 따로 붙인다. 본문에 고정 문구 내용을 반복하지 말 것:\n").append(brand.getFixedText()).append('\n');
    }
    sb.append("\n[최근에 다룬 주제 — 겹치지 않게 다른 주제를 고를 것]\n");
    if (recentTopics.isEmpty()) sb.append("(없음)\n");
    for (String t : recentTopics) sb.append("- ").append(t).append('\n');
    sb.append("\n[이미지 지시(한국어 원문)]\n").append(rule.getImagePrompt()).append('\n');
    sb.append("\n아래 JSON 형식으로만 답한다:\n")
        .append("{\"topic\": \"이번 글의 주제(30자 이내)\", ")
        .append("\"body\": \"인스타그램 본문(줄바꿈은 \\\\n, 해시태그·고정 문구 제외)\", ")
        .append("\"hashtags\": [\"#해시태그\"").append(rule.isAiHashtags() ? " 최대 " + MAX_AI_HASHTAGS + "개" : " 빈 배열").append("], ")
        .append("\"image_prompt_en\": \"이미지 지시를 따르되 이번 주제에 맞게 옷·표정·자세·장면만 바꾼 영어 이미지 생성 프롬프트"
            + "(쉼표로 구분한 묘사, 80단어 이내, 글자·로고 금지)\"}");
    return sb.toString();
  }

  /** LLM을 한 번 부르고 검사한다. 실패하면 CaptionRejected(사유)를 던진다. */
  public Generated generate(SnsAutomationRule rule, SnsBrandPreset brand, List<String> recentTopics) {
    String prompt = buildPrompt(rule, brand, recentTopics);
    NewStartLlmCompletion completion = transport.complete(systemPrompt(), prompt, 4000);
    if (completion == null || !completion.ok()) {
      throw new CaptionRejected("문구 생성 호출 실패: " + (completion == null ? "응답 없음" : completion.errorMessage()));
    }
    JsonNode json = parseJson(completion.text());
    String topic = text(json, "topic");
    String body = text(json, "body").replace("\\n", "\n").strip();
    String imagePromptEn = text(json, "image_prompt_en").strip();
    if (body.isEmpty()) throw new CaptionRejected("본문이 비어 있습니다.");
    if (imagePromptEn.isEmpty()) throw new CaptionRejected("이미지 프롬프트가 비어 있습니다.");
    validateBody(body, brand);
    String caption = composeCaption(body, brand);
    List<String> ai = new ArrayList<>();
    if (rule.isAiHashtags() && json.has("hashtags") && json.get("hashtags").isArray()) {
      json.get("hashtags").forEach(n -> ai.add(n.asText()));
    }
    String hashtags = composeHashtags(rule.fixedHashtagList(), ai);
    if ((caption + "\n\n" + hashtags).length() > MAX_CAPTION_CHARS) {
      throw new CaptionRejected("문구가 인스타그램 길이 제한(2,200자)을 넘습니다.");
    }
    return new Generated(topic.isEmpty() ? null : truncate(topic, 200), body, caption, hashtags,
        truncate(imagePromptEn, 2000), completion.model(), prompt);
  }

  public void validateBody(String body, SnsBrandPreset brand) {
    String lower = body.toLowerCase(Locale.ROOT);
    for (String banned : brand.bannedPhraseList()) {
      if (lower.contains(banned.toLowerCase(Locale.ROOT))) {
        throw new CaptionRejected("금지 표현이 들어 있습니다: " + banned);
      }
    }
    Matcher m = PRICE_LIKE.matcher(body);
    if (m.find()) {
      throw new CaptionRejected("본문에 가격·할인·비율 같은 숫자가 들어 있습니다: " + m.group());
    }
  }

  public static String composeCaption(String body, SnsBrandPreset brand) {
    String fixed = brand.getFixedText() == null ? "" : brand.getFixedText().strip();
    if (fixed.isEmpty()) return body.strip();
    return brand.getFixedTextPosition() == SnsBrandPreset.FixedTextPosition.TOP
        ? fixed + "\n\n" + body.strip()
        : body.strip() + "\n\n" + fixed;
  }

  /** 고정 해시태그(앞, 최대 5) + AI 해시태그(최대 10), 중복 제거, 전체 30개 이하. */
  public static String composeHashtags(List<String> fixed, List<String> ai) {
    Set<String> out = new LinkedHashSet<>();
    for (String f : fixed) addTag(out, f);
    int added = 0;
    for (String a : ai) {
      if (added >= MAX_AI_HASHTAGS) break;
      if (addTag(out, a)) added++;
    }
    return String.join(" ", out.stream().limit(MAX_HASHTAGS).toList());
  }

  private static boolean addTag(Set<String> out, String raw) {
    if (raw == null) return false;
    Matcher m = HASHTAG.matcher(raw.trim());
    if (!m.matches()) return false;
    return out.add("#" + m.group(1));
  }

  private JsonNode parseJson(String text) {
    if (text == null) throw new CaptionRejected("응답이 비어 있습니다.");
    String t = text.strip();
    int start = t.indexOf('{');
    int end = t.lastIndexOf('}');
    if (start < 0 || end <= start) throw new CaptionRejected("응답이 JSON 형식이 아닙니다.");
    try {
      return mapper.readTree(t.substring(start, end + 1));
    } catch (Exception e) {
      throw new CaptionRejected("응답 JSON을 읽을 수 없습니다.");
    }
  }

  private static String text(JsonNode json, String field) {
    JsonNode n = json.get(field);
    return n == null || n.isNull() ? "" : n.asText("");
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) sb.append("- ").append(label).append(": ").append(value.strip()).append('\n');
  }

  private static String truncate(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }
}
