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

  /** §10 guided interview 응답 상한(난독 입력 방지용 최소 가드). */
  static final int MAX_INTERVIEW_FIELD_CHARS = 1000;
  static final int MAX_INTERVIEW_EVENT_ROWS = 20;
  static final int MAX_INTERVIEW_EVIDENCE_ITEMS = 20;

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

  /**
   * §13/§14/§15 — guided interview 초안 계약: FACT/CLAIM/CONCERN/REQUEST 분리, 위법 단정 금지,
   * "확인을 요청합니다" 문체, 고정 섹션 구조, 첫 줄 "제목:" 출력(복사용 분리 파싱 경계).
   */
  static final String INTERVIEW_SYSTEM_PROMPT = SYSTEM_PROMPT
      + " The interview answer sheet is internally labelled FACT / CLAIM / CONCERN / REQUEST;"
          + " keep that separation in the draft: what the other party said is reported as"
          + " 주장했다는 내용 (not established truth), never promoted to fact."
          + " Never conclude illegality or legal violations (\"위법입니다\", \"법을 위반했습니다\" 같은"
          + " 확정 금지); instead ask for verification, e.g. \"이 연락이 적절한 절차인지 확인을"
          + " 요청합니다\"."
          + " Output format is strict: first line \"제목: <40자 이내 제목>\", then a line \"본문:\","
          + " then these Korean sections in order: 사건 개요 / 시간순 사실관계 / 금융회사·채권사 대응"
          + " / 현재 상황 / 첨부 가능한 증거 / 확인을 요청하는 사항 / 원하는 해결."
          + " Style: short sentences, chronological, fact-centered, tied to evidence the user"
          + " actually holds. Soften insults or excessive blame, but never delete the user's"
          + " actual harm or grievance.";

  private final WikiAiRouterClient wikiAiRouterClient;
  private final List<ComplaintSample> samples;
  private final ManualGuide manual;
  private final OfficialGuide officialGuide;

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
    this.officialGuide = parseOfficial(root.path("officialRouting"));
  }

  /** §7 공식 소관 안내(menuNo 201178/201179/201180, 2026-09-25 브라우저 조회). 리소스에 없으면 null. */
  private static OfficialGuide parseOfficial(JsonNode o) {
    if (o.isMissingNode()) {
      return null;
    }
    List<OfficialSource> sources = new ArrayList<>();
    for (JsonNode s : o.path("sources")) {
      sources.add(new OfficialSource(s.path("name").asText(), s.path("url").asText()));
    }
    List<OtherAgencyRoute> others = new ArrayList<>();
    for (JsonNode a : o.path("otherAgencies")) {
      others.add(new OtherAgencyRoute(a.path("matter").asText(), a.path("agency").asText()));
    }
    java.util.Map<String, String> byCategory = new java.util.LinkedHashMap<>();
    o.path("byCategory").fieldNames().forEachRemaining(k ->
        byCategory.put(k, o.path("byCategory").path(k).asText()));
    return new OfficialGuide(o.path("authority").asText(), o.path("retrievedAt").asText(),
        o.path("note").asText(null), List.copyOf(sources), o.path("consultFirst").asText(null),
        o.path("fssScope").asText(null), o.path("processingTime").asText(null),
        List.copyOf(others), java.util.Map.copyOf(byCategory));
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

  /** §7 공식 소관 안내(금감원 공식 페이지 요약). 리소스에 없으면 null. */
  public OfficialGuide officialGuide() {
    return officialGuide;
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
    return new ComposeResult(true, completion.text(), "", completion.text(), null, completion.model());
  }

  /**
   * §10 guided interview 응답만으로 민원 초안을 만든다(전자책 예시 미사용 — §14 고정 섹션 구조).
   * 입력이 없거나 상한을 넘기면 AI를 호출하지 않고 사용자용 안내만 반환한다.
   */
  public ComposeResult composeInterview(Interview interview) {
    if (interview == null || blank(interview.facts())) {
      return ComposeResult.failed("먼저 어떤 일이 있었는지 적어 주세요.");
    }
    String overflow = validateInterview(interview);
    if (overflow != null) {
      return ComposeResult.failed(overflow);
    }
    WikiAiRouterClient.Completion completion = wikiAiRouterClient.complete(
        INTERVIEW_SYSTEM_PROMPT, buildInterviewPrompt(interview), MAX_COMPOSED_CHARS);
    if (!completion.ok()) {
      return ComposeResult.failed(completion.errorMessage());
    }
    Draft draft = parseDraft(completion.text());
    return new ComposeResult(true, completion.text(), draft.subject(), draft.body(),
        null, completion.model());
  }

  private static boolean blank(String s) {
    return s == null || s.strip().isEmpty();
  }

  private static String validateInterview(Interview interview) {
    java.util.function.Predicate<String> tooLong =
        s -> s != null && s.strip().length() > MAX_INTERVIEW_FIELD_CHARS;
    if (tooLong.test(interview.facts()) || tooLong.test(interview.company())
        || tooLong.test(interview.contacts()) || tooLong.test(interview.ownResponses())
        || tooLong.test(interview.outcome())) {
      return "한 항목은 " + MAX_INTERVIEW_FIELD_CHARS + "자 이내로 적어 주세요.";
    }
    if (interview.events() != null && interview.events().size() > MAX_INTERVIEW_EVENT_ROWS) {
      return "날짜 기록은 최대 " + MAX_INTERVIEW_EVENT_ROWS + "개까지 추가할 수 있습니다.";
    }
    if (interview.evidence() != null && interview.evidence().size() > MAX_INTERVIEW_EVIDENCE_ITEMS) {
      return "증거 항목은 최대 " + MAX_INTERVIEW_EVIDENCE_ITEMS + "개까지 선택할 수 있습니다.";
    }
    return null;
  }

  /** §13 라벨(FACT/CLAIM/CONCERN/REQUEST)을 유지하며 인터뷰지를 프롬프트로 조립한다. */
  static String buildInterviewPrompt(Interview i) {
    StringBuilder sb = new StringBuilder();
    sb.append("[상황 분류(FACT)] ").append(clean(i.category())).append('\n');
    sb.append("[FACT — 사용자 본인의 진술. 초안의 사실은 오직 아래 내용뿐]\n");
    sb.append("무슨 일이 있었는지: ").append(clean(i.facts())).append('\n');
    sb.append("어느 금융회사/채권사: ").append(clean(i.company())).append('\n');
    sb.append("[타임라인(FACT)]\n");
    if (i.events() == null || i.events().isEmpty()) {
      sb.append("(사용자가 추가한 날짜 기록 없음)\n");
    } else {
      for (EventRow e : i.events()) {
        sb.append("- ").append(clean(e.date())).append(" | ").append(clean(e.party()))
            .append(" | ").append(clean(e.happened()));
        if (!blank(e.evidence())) {
          sb.append(" | 증거: ").append(clean(e.evidence()));
        }
        sb.append('\n');
      }
    }
    sb.append("[상대방 연락·행위 및 상대방이 주장한 내용(CLAIM — 사실로 확정하지 말고 '~라고"
        + " 주장/안내했다'로 보고할 것)]\n").append(clean(i.contacts())).append('\n');
    sb.append("[사용자가 이미 대응한 내용(FACT)]\n").append(clean(i.ownResponses())).append('\n');
    sb.append("[사용자가 실제로 보유한다고 밝힌 증거만 — 없는 증거를 가정해 적지 말 것]\n");
    if (i.evidence() == null || i.evidence().isEmpty()) {
      sb.append("(증거 없음/확인 안 됨)\n");
    } else {
      for (String e : i.evidence()) {
        sb.append("- ").append(clean(e)).append('\n');
      }
    }
    sb.append("[CONCERN/REQUEST — 사용자가 문제라고 생각하는 점과 원하는 해결]\n")
        .append(clean(i.outcome())).append('\n');
    sb.append("\n위 인터뷰 내용만으로 §14 섹션 구조의 금감원 민원 초안을 작성하세요."
        + " 없는 날짜·금액·기관명은 [ … 확인 필요 ]로 표시하고, 상대방 발언은 CLAIM으로,"
        + " 사용자 불만은 CONCERN으로, 요청은 REQUEST로 분리해 다룹니다.");
    return sb.toString();
  }

  private static String clean(String s) {
    return s == null ? "" : s.strip().replace("\r\n", "\n");
  }

  /** strict parse: "제목: ..." 첫 줄과 "본문:" 경계를 찾는다. 실패 시 원문 전체를 본문으로 둔다. */
  static Draft parseDraft(String composed) {
    String text = composed == null ? "" : composed.strip();
    String[] lines = text.split("\r?\n");
    String subject = "";
    for (int idx = 0; idx < lines.length; idx++) {
      String line = lines[idx].strip();
      if (subject.isEmpty() && (line.startsWith("제목:") || line.startsWith("제목："))) {
        subject = line.substring(3).strip();
        continue;
      }
      if (!subject.isEmpty() && (line.equals("본문") || line.startsWith("본문:")
          || line.startsWith("본문："))) {
        int colon = Math.max(line.indexOf(':'), line.indexOf('：'));
        String inline = colon >= 0 ? line.substring(colon + 1).strip() : "";
        StringBuilder body = new StringBuilder();
        if (!inline.isEmpty()) {
          body.append(inline).append('\n');
        }
        for (int j = idx + 1; j < lines.length; j++) {
          body.append(lines[j]).append('\n');
        }
        return new Draft(subject, body.toString().strip());
      }
    }
    if (!subject.isEmpty()) {
      StringBuilder body = new StringBuilder();
      for (int j = 0; j < lines.length; j++) {
        String line = lines[j].strip();
        if (line.startsWith("제목:") || line.startsWith("제목：")) {
          continue;
        }
        body.append(lines[j]).append('\n');
      }
      return new Draft(subject, body.toString().strip());
    }
    return new Draft("", text);
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

  /**
   * 성공 시 composedText(원문)·subject·body만 의미 있다. V1 자유서술 경로는 subject가 빈 문자열이고
   * body는 composedText 전체다. 실패 시 errorMessage만 의미 있으며 사용자 원문·내부 상세를 담지 않는다.
   */
  public record ComposeResult(boolean ok, String composedText, String subject, String body,
      String errorMessage, String model) {
    public static ComposeResult failed(String errorMessage) {
      return new ComposeResult(false, "", "", "", errorMessage, null);
    }
  }

  /** parseDraft 결과 — 복사용 제목/본문 분리(UI §19). 파싱 실패 시 subject="", body=원문 전체. */
  public record Draft(String subject, String body) {}

  /** §10 guided interview 응답(7문항). 값은 request→response 왕복만 하고 어디에도 저장하지 않는다. */
  public record Interview(String category, String facts, String company, List<EventRow> events,
      String contacts, String ownResponses, List<String> evidence, String outcome) {}

  /** §12 타임라인 1행 — 날짜/상대방/발생한 일/증거. */
  public record EventRow(String date, String party, String happened, String evidence) {}

  /** §7 공식 소관 안내(금감원 공식 페이지 출처). */
  public record OfficialGuide(String authority, String retrievedAt, String note,
      List<OfficialSource> sources, String consultFirst, String fssScope, String processingTime,
      List<OtherAgencyRoute> otherAgencies, java.util.Map<String, String> byCategory) {}

  public record OfficialSource(String name, String url) {}

  public record OtherAgencyRoute(String matter, String agency) {}
}
