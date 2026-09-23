package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 금감원 민원 도우미 V1 — 전자책 실전 민원 예시(EBK-6-3 3종)를 구조 참조로만 삼아,
 * 사용자가 직접 작성한 사실을 금감원 민원 제출용 문장으로 정리해 준다.
 *
 * <p>출처: {@code newstart/fss/fss_complaint_sources.json} — EBOOK_FULLTEXT.txt verbatim 사본
 * (AI 재생성물 아님, provenance 포함). 민원 원문은 영속화하지 않는다(무상태 request→response).
 *
 * <p>프라이버시: 이 클래스와 controller·{@link WikiAiRouterClient} 경로에는 로그가 없다 —
 * 사용자 민원 본문은 application log/analytics/debug log에 남지 않는다(회귀 테스트로 고정).
 * 에러 응답에도 사용자 원문을 넣지 않는다.
 */
@Service
public class FssComplaintHelperService {

  /** 민원 상황 입력 상한. */
  static final int MAX_SITUATION_CHARS = 4000;
  /** AI 정리문 출력 상한. */
  static final int MAX_COMPOSED_CHARS = 6000;

  private static final String SOURCES_RESOURCE = "newstart/fss/fss_complaint_sources.json";

  /** §5 AI PROMPT CONTRACT — 사실 창작성 절대 금지 + 확인 필요 마커 + 결과 단정 금지. */
  static final String SYSTEM_PROMPT =
      "You are assisting the user in organizing a complaint to the Financial Supervisory Service"
          + " (금융감독원). Use the selected ebook example ONLY as a structural and writing reference"
          + " (문단 구성·표현 방식·문제 제기 방식). Never invent facts. Never invent: dates, amounts,"
          + " communications (전화통화·담당자·금융기관 발언), official rules, legal conclusions,"
          + " reduction rates (감면율), financial outcomes, or 피해 사실."
          + " The facts of the complaint come exclusively from what the user wrote; preserve the"
          + " user's factual meaning without changing it."
          + " If necessary information is missing, mark it as needing confirmation"
          + " (예: \"[날짜 확인 필요]\", \"[금액 확인 필요]\") instead of fabricating it."
          + " Do not assert complaint results or acceptance (\"받아줄 것입니다\", \"인정될 것입니다\","
          + " \"금융기관이 잘못했습니다\" 같은 단정 금지) — 작성 지원과 결과 판단을 구분한다."
          + " CLASSIFY FIRST, INFORM SECOND, JUDGE ONLY WHEN ASKED."
          + " Output only the organized Korean complaint draft text, no explanations.";

  private final WikiAiRouterClient wikiAiRouterClient;
  private final List<ComplaintSample> samples;
  private final ManualGuide manual;

  public FssComplaintHelperService(WikiAiRouterClient wikiAiRouterClient) {
    this.wikiAiRouterClient = wikiAiRouterClient;
    JsonNode root = loadSources();
    List<ComplaintSample> loaded = new ArrayList<>();
    for (JsonNode s : root.path("samples")) {
      loaded.add(new ComplaintSample(
          s.path("id").asText(), s.path("ebookRef").asText(), s.path("sourceLines").asText(),
          s.path("title").asText(), s.path("situation").asText(), s.path("subject").asText(),
          s.path("body").asText(), ""));
    }
    this.samples = List.copyOf(loaded);
    JsonNode m = root.path("manual");
    List<ManualStep> steps = new ArrayList<>();
    for (JsonNode st : m.path("steps")) {
      steps.add(new ManualStep(
          st.path("no").asInt(), st.path("do").asText(), st.path("ebook").asText(),
          st.path("caution").isNull() ? null : st.path("caution").asText(null)));
    }
    JsonNode g = m.path("siteGuide");
    List<SiteStep> siteSteps = new ArrayList<>();
    for (JsonNode st : g.path("steps")) {
      siteSteps.add(new SiteStep(st.path("no").asInt(), st.path("text").asText()));
    }
    List<String> tips = new ArrayList<>();
    for (JsonNode t : g.path("tips")) {
      tips.add(t.asText());
    }
    SiteGuide siteGuide = g.isMissingNode() ? null : new SiteGuide(
        g.path("source").asText(), g.path("url").asText(null),
        g.path("imageNote").asText(null), List.copyOf(siteSteps), List.copyOf(tips));
    this.manual = new ManualGuide(m.path("ebookRef").asText(), m.path("intro").asText(),
        List.copyOf(steps), m.path("note").asText(null), siteGuide);
  }

  private static JsonNode loadSources() {
    try (InputStream in = new ClassPathResource(SOURCES_RESOURCE).getInputStream()) {
      return new ObjectMapper().readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("fss complaint sources resource missing: " + SOURCES_RESOURCE, e);
    }
  }

  /** 전자책 민원 예시 목록(카드 노출용). */
  public List<ComplaintSample> samples() {
    return samples;
  }

  public ManualGuide manual() {
    return manual;
  }

  /**
   * 선택한 예시 구조 + 사용자 사실만 전달해 민원문을 정리한다. 사용자 원문·내부 상세를
   * 에러 메시지에 넣지 않는다.
   */
  public ComposeResult compose(String sampleId, String rawSituation) {
    ComplaintSample sample = samples.stream()
        .filter(s -> s.id().equals(sampleId == null ? "" : sampleId.strip()))
        .findFirst()
        .orElse(null);
    if (sample == null) {
      return ComposeResult.failed("선택한 민원 예시를 찾을 수 없습니다. 다시 선택해 주세요.");
    }
    String situation = rawSituation == null ? "" : rawSituation.strip();
    if (situation.isEmpty()) {
      return ComposeResult.failed("있었던 일을 조금만 적어 주세요. 정리하지 않아도 괜찮습니다.");
    }
    if (situation.length() > MAX_SITUATION_CHARS) {
      return ComposeResult.failed(
          "입력이 너무 깁니다. " + MAX_SITUATION_CHARS + "자 이내로 나눠서 적어 주세요.");
    }
    WikiAiRouterClient.Completion completion =
        wikiAiRouterClient.complete(SYSTEM_PROMPT, buildPrompt(sample, situation), MAX_COMPOSED_CHARS);
    if (!completion.ok()) {
      return ComposeResult.failed(completion.errorMessage());
    }
    return new ComposeResult(true, completion.text(), null, completion.model());
  }

  static String buildPrompt(ComplaintSample sample, String situation) {
    return "[구조 참고용 전자책 민원 예시 — 문단 구성·표현만 참조. 예시의 기관명·금액·날짜를"
        + " 사용자에게 옮겨 쓰지 말 것]\n"
        + "제목: " + sample.subject() + "\n"
        + sample.body() + "\n\n"
        + "[사용자가 직접 작성한 사실 — 민원문에 사용할 사실은 오직 이 내용뿐]\n"
        + situation + "\n\n"
        + "위 예시의 구조를 참고하여, 사용자가 작성한 사실만으로 금감원 민원 제출용 문장을"
        + " 정리해 주세요. 없는 사실은 [ … 확인 필요 ] 형태로 표시하세요.";
  }

  /** 전자책 EBK-6-3 샘플 1종(카드). body/subject는 원문 verbatim. */
  public record ComplaintSample(String id, String ebookRef, String sourceLines, String title,
      String situation, String subject, String body, String preview) {
    public ComplaintSample {
      preview = derivePreview(body);
    }

    /** 카드 미리보기: 본문 첫 문장(원문 발췌, 신규 문장 아님). */
    private static String derivePreview(String body) {
      String b = body == null ? "" : body;
      int cut = b.indexOf('\n');
      String first = cut < 0 ? b : b.substring(0, cut);
      return first.length() > 120 ? first.substring(0, 120) + "…" : first;
    }
  }

  /** 전자책 실전 루프(EBK-6-1/6-2) 단계. 사이트 클릭 절차는 전자책에 없어 임의 추가하지 않는다. */
  public record ManualStep(int no, String action, String ebook, String caution) {}

  /** 카페 2961 사이트 접수 단계(원문 텍스트 보존, 캡처 이미지 미포함). */
  public record SiteStep(int no, String text) {}

  public record SiteGuide(String source, String url, String imageNote,
      List<SiteStep> steps, List<String> tips) {}

  public record ManualGuide(String ebookRef, String intro, List<ManualStep> steps, String note,
      SiteGuide siteGuide) {}

  /** 성공 시 composedText만, 실패 시 errorMessage만 의미 있다(원문·내부 상세 미포함). */
  public record ComposeResult(boolean ok, String composedText, String errorMessage, String model) {
    public static ComposeResult failed(String errorMessage) {
      return new ComposeResult(false, "", errorMessage, null);
    }
  }
}
