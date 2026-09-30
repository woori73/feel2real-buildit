package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NewStart AI 질문 — 회원용 {@code /api/newstart/ai/chat}과 NewStart 지식 DB를 연결하는 Q&A service.
 *
 * <p>질문이 오면 지피지기 Agent V1의 강제 조사 사다리({@link JipijigiResearchService})로
 * 승인(ACTIVE)된 지식·공식 자료·수집 사례를 먼저 검색하고, 그 근거를 프롬프트에 조립한 뒤
 * NewStart 전용 {@link NewStartLlmClient}(NVIDIA NIM Lightning DIRECT)에 위임한다. 근거 없는
 * 모델 자체 지식 단정 answers를 억제하는 것이 목적이다.
 *
 * <p>REVIEW 자료는 검색 대상이 아니다 — 사다리의 ACTIVE 필터를 유지하고,
 * 승인(REVIEW→ACTIVE)은 admin 승인 워크플로를 거친 자료만 이곳에 도달한다.
 *
 * <p>LLM transport는 NewStart 독립 경로다(공용 Wiki AI Router를 경유하지 않는다). 조사 사다리와
 * 근거 우선순위는 그대로 두고, 마지막 모델 호출만 NVIDIA DIRECT → 기술적 실패 시 OpenRouter
 * Qwen DIRECT 1회로 교체된다. NewStart에 model ID hard-code는 두지 않고 설정으로만 갖는다.
 */
@Service
public class NewStartAiChatService {

  private static final Logger log = LoggerFactory.getLogger(NewStartAiChatService.class);

  /** 질문 길이 상한. 과대 프롬프트 남용을 막는 최소한의 가드다. */
  static final int MAX_QUESTION_CHARS = 2000;
  /** 답변 출력 상한. {@link NewStartLlmClient}가 응답에 적용한다. */
  static final int MAX_ANSWER_CHARS = 4000;
  /** 컨텍스트에 실리는 근거 총량 상한 — 프롬프트 폭주를 막는다. */
  static final int MAX_CONTEXT_EVIDENCE = 12;
  /** 공식 자료 상한. 이 값이 없으면 공식 8건+내부 공식 판정분이 12개를 모두 채워 사례가 못 들어온다. */
  static final int MAX_OFFICIAL_EVIDENCE = 8;
  /** 공식 근거가 있을 때 확인·보완용으로 남기는 최소 건수(사례 근거가 컨텍스트를 다 채워도 유지). */
  static final int MIN_OFFICIAL_EVIDENCE = 3;
  /** 답변에 붙이는 출처 상한. */
  static final int MAX_SOURCES = 8;
  /** 근거 1건 요약 상한. */
  static final int MAX_EVIDENCE_CHARS = 400;
  /** 한국어 혼합 텍스트의 대략적인 token 크기(정밀 값은 provider의 usage 응답). */
  private static final int APPROX_CHARS_PER_TOKEN = 3;

  private static final String SYSTEM_PROMPT =
      "너는 새출발기금·부실차주·채무조정 정보를 도우는 한국어 안내자다. 이해하기 쉬운 문장으로 답한다."
          + " 답변에는 사용자에게 바로 보여도 되는 최종 문장만 한국어로 담는다."
          + " 상황 분석·문제 해석·'Here's a thinking process' 같은 사고 과정이나 영문 메모는 출력하지 않는다."
          + " 확인되지 않은 정책 내용을 확정적으로 말하지 않고, 모르는 내용은 모른다고 말한다."
          + " 개인 재무정보를 임의로 만들어내지 않으며, 중요한 판단은 관련 기관 안내를 확인하도록 안내한다."
          + " 카페 경험담이나 근거 없는 자료를 공식 정책처럼 말하지 않는다."
          + " 사용자가 추심 피해(집중 전화, 욕설, 협박), 채권사 대응, 금감원 민원 작성이나 접수를"
          + " 언급하면 새출발기금 홈의 추심 대응 - 금감원 민원 도우미 화면(/newstart/fss-helper)을"
          + " 안내한다. 이 도우미에서 상황을 단계별로 정리하고 민원 초안과 증거 타임라인을 만들고"
          + " 금감원 접수 단계 안내를 받을 수 있다."
          + " 다만 민원 제출과 본인인증은 반드시 금융감독원 공식 사이트에서 사용자 본인이 직접 하며,"
          + " 위법 여부나 민원 결과는 단정하지 않는다."
          + " 아래 [NewStart 지식베이스 근거]가 제공되면 그 근거만으로 답의 핵심을 뒷받침하고,"
          + " 공식 자료는 '공식 안내에 따르면', 카페 회원 경험은 '카페 사례에서는'처럼 출처 성격을 명시해 구분한다."
          + " 답변 본문에는 '[KB1]', '[KB2 공식]'처럼 대괄호 번호·키워드 인용표를 쓰지 말고,"
          + " 근거는 위와 같이 출처 성격만 자연어로 밝힌다."
          + " 근거에 없는 내용은 근거가 없다고 밝히고 공식 기관 안내 확인을 권유한다."
          + " 운영자 경험은 '운영자 경험에서는', 전자책은 '전자책에서는'처럼 밝힌다."
          + " [답변 순서] 정부기관 설명부터 길게 시작하지 않는다. 먼저 질문자가 처한 상황을 한두 문장으로 짚고,"
          + " 근거 목록에서 전자책·운영자 경험·카페/상담 사례 중 질문과 직접 관련된 것을 찾아 그 사례에서 실제로 어떻게 진행됐고"
          + " 어떻게 대응했는지를 먼저 설명한다. 이어서 지금 확인하거나 할 수 있는 다음 행동을 알려주고,"
          + " 그 뒤에 '공식 자료' 근거로 현재 기준을 확인·보완한다. 사례와 공식 기준이 다르면 어느 시점의 사례인지와 차이를 둘 다 보여준다."
          + " 근거 목록은 앞쪽이 질문과 가장 직접적인 사례·경험이고 뒤쪽 '공식 자료'는 확인·보완용이다."
          + " 사례 근거가 있는데 이를 무시하고 공식 규정만 답하지 않는다. 사례가 질문과 직접 관련이 없으면 억지로 끼우지 말고"
          + " 비슷한 사례를 찾지 못했다고 밝힌다."
          + " 단일 사례는 '이 사례에서는', '당시에는', '일부 사례에서 관찰'처럼 수준을 밝히고 모든 경우의 규칙으로 단정하지 않는다."
          + " 카페 글 속 추정·주장은 확정 사실로 쓰지 않는다. 근거에 작성 시점이 있으면 시점을 함께 말한다."
          + " 근거에 없는 전화번호·URL·금액·기한·날짜를 만들어내지 않는다."
          + " 재산 은닉, 허위 거래, 가장행위, 심사 회피처럼 부적절한 행동의 실행 방법은 사례에 있어도 안내하지 않고 위험할 수 있다고만 알린다.";

  private final NewStartLlmClient newStartLlmClient;
  private final JipijigiResearchService researchService;

  public NewStartAiChatService(NewStartLlmClient newStartLlmClient, JipijigiResearchService researchService) {
    this.newStartLlmClient = newStartLlmClient;
    this.researchService = researchService;
  }

  /** 질문 하나를 조사 사다리 → 근거 컨텍스트 → NewStart DIRECT LLM 흐름으로 답변한다. 실패는 result-object로 보고한다. */
  public NewStartAiAnswer ask(String rawQuestion) {
    String question = rawQuestion == null ? "" : rawQuestion.strip();
    if (question.isEmpty()) {
      return NewStartAiAnswer.failed("질문을 입력해 주세요.");
    }
    if (question.length() > MAX_QUESTION_CHARS) {
      return NewStartAiAnswer.failed("질문이 너무 깁니다. " + MAX_QUESTION_CHARS + "자 이내로 입력해 주세요.");
    }

    ResearchPack pack;
    long tStart = System.nanoTime();
    try {
      pack = researchService.research(question);
    } catch (RuntimeException e) {
      // DB 장애로 조사를 못 하면 근거 없이 답하는 것보다 실패를 정직하게 알린다.
      return NewStartAiAnswer.failed("지식베이스 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
    long tRag = System.nanoTime();

    List<Evidence> ordered = orderedEvidence(pack);
    long tEvidence = System.nanoTime();
    String evidenceContext = ordered.isEmpty() ? "" : buildEvidenceContext(ordered);
    String systemPrompt = ordered.isEmpty() ? SYSTEM_PROMPT : SYSTEM_PROMPT + "\n\n[NewStart 지식베이스 근거]\n" + evidenceContext;
    long tPrompt = System.nanoTime();

    NewStartLlmCompletion completion =
        newStartLlmClient.complete(systemPrompt, question, MAX_ANSWER_CHARS);
    long tLlm = System.nanoTime();

    // 구간 실측(2026-09-28 latency 지시). 질문 원문·키·reasoning_content는 남기지 않고 길이와 ms만 남긴다.
    // SERIALIZATION/TOTAL은 이 로그로 안 잡힌다(controller + 클라이언트 time_total로 분리 보고).
    int promptChars = systemPrompt.length() + question.length();
    log.info("newstart ai chat timing ragMs={} evidenceMs={} promptMs={} llmMs={} serviceMs={} "
            + "questionChars={} evidenceCount={} evidenceChars={} systemPromptChars={} promptChars={} "
            + "inputTokenEstimate={} historyMessages=0 answerChars={} model={} ok={}",
        ms(tRag - tStart), ms(tEvidence - tRag), ms(tPrompt - tEvidence), ms(tLlm - tPrompt), ms(tLlm - tStart),
        question.length(), ordered.size(), evidenceChars(ordered), systemPrompt.length(), promptChars,
        promptChars / APPROX_CHARS_PER_TOKEN, completion.text().length(), completion.model(), completion.ok());

    if (!completion.ok()) {
      return NewStartAiAnswer.failed(completion.errorMessage(), toSources(ordered));
    }
    return new NewStartAiAnswer(true, sanitizeAnswer(completion.text(), evidenceContext), null, completion.model(),
        toSources(ordered));
  }

  private static final java.util.regex.Pattern KB_CITATION =
      java.util.regex.Pattern.compile("\\s*\\[\\s*KB\\s*\\d*[^\\]\\n]{0,20}\\]");
  private static final java.util.regex.Pattern PHONE =
      java.util.regex.Pattern.compile("(?<![\\d-])(0\\d{1,2}-\\d{3,4}-\\d{4}|1[5-9]\\d{2}-\\d{4})(?![\\d-])");

  /**
   * 모델 출력 결정적 보정(프롬프트 금지 지시가 어겨질 때의 안전망):
   * 모델이 임의로 만든 [KB2 공식]류 인용표를 제거하고, 근거 컨텍스트에 없는 전화번호는 지운다(hallucination 방지).
   * 근거에 실제로 있는 번호(예: 기관 대표번호)는 그대로 둔다.
   */
  static String sanitizeAnswer(String text, String evidenceContext) {
    if (text == null) return "";
    String out = KB_CITATION.matcher(text).replaceAll("");
    String ctx = evidenceContext == null ? "" : evidenceContext;
    java.util.regex.Matcher m = PHONE.matcher(out);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
          ctx.contains(m.group(1)) ? m.group(1) : "(번호는 해당 기관 공식 사이트에서 확인)"));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static long ms(long nanos) {
    return Math.round(nanos / 1_000_000.0);
  }

  private static int evidenceChars(List<Evidence> ordered) {
    int total = 0;
    for (Evidence e : ordered) {
      total += (e.getTitle() == null ? 0 : e.getTitle().length())
          + (e.getExcerpt() == null ? 0 : e.getExcerpt().length());
    }
    return total;
  }

  /**
   * 근거 조립 (운영원칙 §2: 실제 사례·경험 먼저 → 현재 공식자료로 뒤에서 확인·보완):
   * 1) 승인 운영 지식/전자책 2) 승인 카페 지식 3) 승인 축적 답변 4) 카페·실증 사례 5) 공식 자료(확인·보완).
   * 같은 (refType, refId) 근거는 먼저 온 우선순위로 1회만 실린다. 출처 라벨(provenance)은 유지하며,
   * 공식 자료가 사례 근거를 밀어내지도, 사례가 공식으로 승격되지도 않는다.
   *
   * <p>공식 자료는 {@value #MAX_OFFICIAL_EVIDENCE}건으로 막고, 공식 근거가 있으면 최소 {@value #MIN_OFFICIAL_EVIDENCE}건은
   * 확인·보완용으로 컨텍스트와 출처 {@value #MAX_SOURCES}건 안에 남긴다(사례가 12칸을 다 채워도 공식 확인이 사라지지 않게).
   */
  static List<Evidence> orderedEvidence(ResearchPack pack) {
    List<Evidence> official = new ArrayList<>();
    Set<String> officialSeen = new LinkedHashSet<>();
    addAll(official, officialSeen, pack.getOfficialHits());
    addAll(official, officialSeen, filterClass(pack.getInternalHits(), SourceClass.OFFICIAL_RULE));

    List<Evidence> supporting = new ArrayList<>();
    Set<String> supportingSeen = new LinkedHashSet<>();
    // 1. 승인 운영 지식/전자책
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.OPERATOR_MATERIAL));
    // 2. ADMIN 승인을 거쳐 ACTIVE된 카페 지식 항목 — 원시 실증 조회(borrowerHits)보다 앞선 커레이션 등급
    // (2026-09-30 GOLDEN-1: 뒤에 두면 운영자 자료+실증 사례에 밀려 출처에서 항상 탈락).
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.BORROWER_CAFE_CASE));
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.CCRS_CAFE_CASE));
    // 3. 운영자 승인 축적 답변 세션
    addAll(supporting, supportingSeen, filterRef(pack.getInternalHits(), RefType.ANSWER_SESSION));
    // 4. 카페/실증 사례 (provenance 유지 — 공식으로 승격 금지)
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.INTERNAL_CASE));
    addAll(supporting, supportingSeen, pack.getBorrowerHits());
    addAll(supporting, supportingSeen, pack.getCcrsHits());

    int officialReserve = Math.min(official.size(), MIN_OFFICIAL_EVIDENCE);
    int supportTake = Math.min(supporting.size(), MAX_CONTEXT_EVIDENCE - officialReserve);
    int officialTake = Math.min(Math.min(official.size(), MAX_OFFICIAL_EVIDENCE), MAX_CONTEXT_EVIDENCE - supportTake);

    List<Evidence> ordered = new ArrayList<>(supporting.subList(0, supportTake));
    ordered.addAll(official.subList(0, officialTake));
    return List.copyOf(ordered);
  }

  private static void addAll(List<Evidence> out, Set<String> seen, List<Evidence> in) {
    if (in == null) return;
    for (Evidence e : in) {
      if (e == null || e.getTitle() == null) continue;
      if (seen.add(e.getRefType() + ":" + e.getRefId())) out.add(e);
    }
  }

  private static List<Evidence> filterClass(List<Evidence> in, SourceClass cls) {
    if (in == null) return List.of();
    return in.stream().filter(e -> e != null && e.getSourceClass() == cls).toList();
  }

  private static List<Evidence> filterRef(List<Evidence> in, RefType type) {
    if (in == null) return List.of();
    return in.stream().filter(e -> e != null && e.getRefType() == type && e.getSourceClass() != SourceClass.OFFICIAL_RULE
            && e.getSourceClass() != SourceClass.OPERATOR_MATERIAL)
        .toList();
  }

  static String buildEvidenceContext(List<Evidence> ordered) {
    StringBuilder sb = new StringBuilder();
    sb.append("(앞쪽=전자책·운영자 경험·카페/실증 사례, 뒤쪽 공식 자료=현재 기준 확인·보완용. 각 자료의 성격 라벨을 답변에서도 구분한다.)\n");
    int i = 1;
    for (Evidence e : ordered) {
      sb.append(i++).append(". [").append(labelOf(e.getSourceClass())).append("] ")
          .append(e.getTitle().replace("\n", " ")).append("\n");
      String when = timeOf(e.getNote());
      if (when != null) sb.append("   시점: ").append(when).append("\n");
      String excerpt = e.getExcerpt() == null ? "" : e.getExcerpt().replace("\n", " ");
      if (excerpt.length() > MAX_EVIDENCE_CHARS) excerpt = excerpt.substring(0, MAX_EVIDENCE_CHARS) + "…";
      if (!excerpt.isBlank()) sb.append("   근거 요지: ").append(excerpt).append("\n");
      if (e.getRefUrl() != null && !e.getRefUrl().isBlank()) sb.append("   출처: ").append(e.getRefUrl()).append("\n");
    }
    return sb.toString();
  }

  private static final java.util.regex.Pattern TIME_NOTE =
      java.util.regex.Pattern.compile("(?:작성 |자료시점=)(\\d{4}-\\d{2}-\\d{2})");

  /** 근거 note에 실린 작성·자료 시점(있으면). 사례와 현재 기준의 시점 차이를 답변이 밝힐 수 있게 컨텍스트에 노출한다. */
  static String timeOf(String note) {
    if (note == null) return null;
    java.util.regex.Matcher m = TIME_NOTE.matcher(note);
    return m.find() ? m.group(1) : null;
  }

  static String labelOf(SourceClass cls) {
    return switch (cls) {
      case OFFICIAL_RULE -> "공식 자료";
      case OPERATOR_MATERIAL -> "운영자·전자책 자료";
      case INTERNAL_CASE -> "실증 사례(DB)";
      case CCRS_CAFE_CASE -> "신복위 카페 사례";
      case BORROWER_CAFE_CASE -> "부실차주 카페 사례";
      case OTHER -> "참고";
    };
  }

  private static List<AnswerSource> toSources(List<Evidence> ordered) {
    List<Evidence> visible = ordered.stream().filter(e -> e.getRefType() != RefType.NONE).toList();
    long officialCount = visible.stream().filter(e -> e.getSourceClass() == SourceClass.OFFICIAL_RULE).count();
    int officialSlots = (int) Math.min(officialCount, MIN_OFFICIAL_EVIDENCE);
    List<AnswerSource> out = new ArrayList<>();
    int support = 0;
    for (Evidence e : visible) {
      if (e.getSourceClass() == SourceClass.OFFICIAL_RULE) continue;
      if (support >= MAX_SOURCES - officialSlots) break;
      out.add(new AnswerSource(labelOf(e.getSourceClass()), e.getTitle(), e.getRefUrl()));
      support++;
    }
    for (Evidence e : visible) {
      if (e.getSourceClass() != SourceClass.OFFICIAL_RULE) continue;
      if (out.size() >= MAX_SOURCES) break;
      out.add(new AnswerSource(labelOf(e.getSourceClass()), e.getTitle(), e.getRefUrl()));
    }
    return List.copyOf(out);
  }

  /** 호출 결과. 성공 시 {@code answer}만 의미 있고 실패 시 항상 빈 문자열이며 {@code errorMessage}만
   * 의미 있다. {@code model}은 transport가 반환한 식별자(검증/로깅용)로, 실패 시 null이다.
   * {@code sources}는 답변 조립에 실제 사용된 근거 출처 목록(최대 {@value #MAX_SOURCES}건)이다. */
  public record NewStartAiAnswer(boolean ok, String answer, String errorMessage, String model,
                                 List<AnswerSource> sources) {
    public static NewStartAiAnswer failed(String errorMessage) {
      return new NewStartAiAnswer(false, "", errorMessage, null, List.of());
    }

    public static NewStartAiAnswer failed(String errorMessage, List<AnswerSource> sources) {
      return new NewStartAiAnswer(false, "", errorMessage, null, sources);
    }
  }

  /** 답변 근거 출처. kind는 공식/운영자료/카페 사례 구분 문자열 — 커뮤니티 근거를 공식처럼 보여주기 위한 라벨. */
  public record AnswerSource(String kind, String title, String url) {}
}
