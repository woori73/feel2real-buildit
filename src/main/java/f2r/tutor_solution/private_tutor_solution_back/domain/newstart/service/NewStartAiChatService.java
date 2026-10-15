package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiQuestionIntent;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NewStart AI 질문 — 회원용 {@code /api/newstart/ai/chat}과 NewStart 지식 DB를 연결하는 Q&A service.
 *
 * <p><b>2단계 답변</b>: {@link #ask}(STEP 1: 전자책+실제 사례 1차 답변)가 먼저 반환·표시되고, 이어서
 * {@link #askOfficial}(STEP 2: 현재 공식자료로 확인·보완)이 별도 호출로 1차 답변 아래에 추가된다. STEP 2가 실패·지연돼도
 * STEP 1은 영향받지 않는다.
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
  /** 질문 신호(focus term)가 있을 때 STEP 2 가 쓰는 공식 자료 상한 — 검색 결과가 많다는 이유로 모두 싣지 않는다. */
  static final int MAX_OFFICIAL_FOCUSED = 5;
  /** 답변에 붙이는 출처 상한. */
  static final int MAX_SOURCES = 8;
  /** STEP 1에서 전자책이 컨텍스트를 다 채워도 직접 관련 실제 사례에 보장하는 슬롯(사례가 그만큼 있을 때). */
  static final int CASE_RESERVE = 6;
  /** STEP 1 출처 목록에서 실제 사례에 보장하는 최소 칸 수(사례가 그만큼 있을 때). */
  static final int SOURCE_CASE_RESERVE = 4;
  /** STEP 2 프롬프트에 싣는 1차 답변 길이 상한. */
  static final int MAX_STEP1_CHARS = 2500;
  /** 근거 1건 요약 상한. */
  static final int MAX_EVIDENCE_CHARS = 400;
  /** 한국어 혼합 텍스트의 대략적인 token 크기(정밀 값은 provider의 usage 응답). */
  private static final int APPROX_CHARS_PER_TOKEN = 3;

  /** STEP 1·2 공통 규칙. 두 단계 모두 근거 밖 내용·사고 과정·번호 인용표·전화번호 생성을 금지한다. */
  private static final String BASE_PROMPT =
      "너는 새출발기금·부실차주·채무조정 정보를 도우는 한국어 안내자다. 이해하기 쉬운 문장으로 답한다."
          + " 답변에는 사용자에게 바로 보여도 되는 최종 문장만 한국어로 담는다."
          + " 상황 분석·문제 해석·'Here's a thinking process' 같은 사고 과정이나 영문 메모는 출력하지 않고,"
          + " 답변 끝에 영문 문구·메모·'official guidance' 같은 꼬리를 붙이지 않는다."
          + " 확인되지 않은 정책 내용을 확정적으로 말하지 않고, 모르는 내용은 모른다고 말한다."
          + " 개인 재무정보를 임의로 만들어내지 않으며, 중요한 판단은 관련 기관 안내를 확인하도록 안내한다."
          + " 카페 경험담이나 근거 없는 자료를 공식 정책처럼 말하지 않는다."
          + " 사용자가 추심 피해(집중 전화, 욕설, 협박), 채권사 대응, 금감원 민원 작성이나 접수를"
          + " 언급하면 새출발기금 홈의 추심 대응 - 금감원 민원 도우미 화면(/newstart/fss-helper)을"
          + " 안내한다. 이 도우미에서 상황을 단계별로 정리하고 민원 초안과 증거 타임라인을 만들고"
          + " 금감원 접수 단계 안내를 받을 수 있다."
          + " 다만 민원 제출과 본인인증은 반드시 금융감독원 공식 사이트에서 사용자 본인이 직접 하며,"
          + " 위법 여부나 민원 결과는 단정하지 않는다."
          + " 답변 본문에는 '[KB1]', '[KB2 공식]'처럼 대괄호 번호·키워드 인용표를 쓰지 말고,"
          + " 근거는 출처 성격만 자연어로 밝힌다."
          + " 근거에 없는 전화번호·URL·금액·기한·날짜를 만들어내지 않는다."
          + " 재산 은닉, 허위 거래, 가장행위, 심사 회피처럼 부적절한 행동의 실행 방법은 사례에 있어도 안내하지 않고 위험할 수 있다고만 알린다.";

  /**
   * STEP 1 — 전자책 + 실제 사례(운영자 경험·카페·상담) 기반 1차 답변. 이 단계 근거에는 공식 자료가 없다:
   * 전자책·사례를 공식 안내처럼 쓰지 못하게 하고, 현재 규정은 뒤 단계에서 확인한다고만 밝힌다.
   */
  static final String CASE_STEP_PROMPT = BASE_PROMPT
      + " [1차 답변 — 전자책·실제 사례] 아래 [NewStart 지식베이스 근거]는 전자책·운영자 경험·카페/상담 사례뿐이며 공식 자료가 아니다."
      + " 전자책은 '전자책에서는', 운영자 경험은 '운영자 경험에서는', 카페 사례는 '카페 사례에서는'처럼 자료 성격 그대로 밝힌다."
      + " 이 단계에서는 '공식 안내에 따르면'·'공식 자료'라고 쓰지 않고 현재 규정을 단정하지 않는다. 현재 규정은 다음 단계에서 따로 확인한다."
      + " 정부기관 설명부터 길게 시작하지 않는다. 먼저 질문자가 처한 상황을 한두 문장으로 짚고, 근거 중 질문과 직접 관련된"
      + " 전자책·실제 사례를 찾아 그 사례에서 실제로 어떻게 진행됐고 어떻게 대응했는지를 먼저 설명한 뒤,"
      + " 지금 확인하거나 할 수 있는 다음 행동을 알려준다."
      + " 사례 근거가 있는데 이를 무시하지 않는다. 직접 관련이 없으면 억지로 끼우지 말고 비슷한 사례를 찾지 못했다고 밝힌다."
      + " 단일 사례는 '이 사례에서는', '당시에는', '일부 사례에서 관찰'처럼 수준을 밝히고 모든 경우의 규칙으로 단정하지 않는다."
      + " 카페 글 속 추정·주장은 확정 사실로 쓰지 않는다. 근거에 작성 시점이 있으면 시점을 함께 말한다."
      + " [답변 순서] ① 질문자의 현재 상황을 한두 문장으로 정확히 짚는다."
      + " ② 질문자가 가장 걱정하는 것에 바로 답한다. 동일하거나 매우 유사한 운영자 직접 경험이 근거에 있으면 가장 먼저 그것으로 답하고,"
      + " 지금 실제 위험이 큰 단계인지·아직 걱정하지 않아도 되는 부분인지·문제가 생기면 쓸 수 있는 대응수단이 무엇인지를 구분해 불안을 줄인다."
      + " ③ 지금 해야 할 행동을 시간 순서대로 번호로 제시한다."
      + " ④ 실제 문제(추심·압류 등)가 생겼을 때의 대응을 알려준다(증거 보존, 금감원 민원 대응, 추심 대응 민원 도우미 안내)."
      + " ⑤ 꼭 필요한 경우에만 추가 설명을 붙인다. 질문과 직접 관련 없는 제도·규정은 설명하지 않는다."
      + " 사용자가 읽고 '그래서 지금 무엇부터 하면 되는지' 바로 알 수 있어야 한다."
      + " 불안을 줄이는 것은 빈 위로가 아니다. '걱정하지 마세요'·'힘내세요' 같은 근거 없는 위로는 쓰지 않고, 운영자 경험이나 충분히 유사한 사례,"
      + " 현재 단계 판단, 행동 방법으로만 줄인다."
      + " 운영자가 어떤 사례에서 그렇게 판단했다는 것은 법적으로 항상 그렇다는 뜻이 아니다. 운영자 경험을 법률 규정이나 보장으로 바꿔 말하지 않고,"
      + " '운영자 경험에서는 이 상황에 대해 ~라고 판단했다'처럼 경험·판단임을 밝히며 그 사례 밖으로 일반화하지 않는다."
      + " 운영자 경험에 소요 기간 같은 수치가 있으면 '운영자 경험상 보통 약 N일'처럼 출처와 함께 그대로 쓰고 다른 숫자를 만들지 않는다.";

  /**
   * STEP 2 — 현재 공식 자료로 1차 답변을 확인·보완한다. 1차 답변은 이미 사용자에게 표시돼 있으므로 다시 쓰거나
   * 정정·삭제하지 않고, 현재 규정 확인·차이·주의사항만 덧붙인다.
   */
  static final String OFFICIAL_STEP_PROMPT = BASE_PROMPT
      + " [2차 답변 — 현재 규정 확인] 질문자에게는 이미 전자책·실제 사례 기반 1차 답변이 표시됐다."
      + " 아래 [NewStart 지식베이스 근거]는 현재 공식 자료뿐이다. 이 공식 자료만으로 1차 답변을 확인·보완한다."
      + " 1차 답변을 반복하거나 삭제·정정하지 말고, 다음 순서로 짧게 쓴다:"
      + " ① 현재 공식 자료에서 확인되는 내용 ② 1차 답변(사례·전자책)과 현재 기준이 다른 점 — 사례 당시와 현재 기준의 시점 차이를 함께 밝힌다"
      + " ③ 지금 진행할 때 추가로 확인·주의할 점."
      + " 공식 근거에 있는 내용만 '공식 안내에 따르면'이라고 쓴다. 공식 근거에 없는 내용은 공식적으로 확인되지 않았다고 밝히고"
      + " 해당 기관 안내 확인을 권한다. 공식자료가 사례와 다르다고 해서 사례를 틀렸다고 단정하지 않고 시점·차이를 나란히 보여준다."
      + " 이 단계는 새로운 장문의 정부자료 설명을 만드는 단계가 아니다. 질문자의 가장 큰 걱정과 1차 답변이 제시한 행동 단계 중 현재 규정으로"
      + " 확인이 필요한 부분, 제도가 바뀐 부분, 중요한 예외만 쓴다. 질문·1차 답변과 직접 관련 없는 공식 규정은 근거에 있어도 쓰지 않는다."
      + " 항목은 5개 이내, 각 1~2문장으로 쓴다. 운영자 경험과 현재 공식 규정이 충돌하면 '운영자 경험은 이 사례 기준, 공식 안내는 다르게 안내한다'처럼"
      + " 둘을 서로 다른 출처로 나란히 표시하고, 운영자 경험을 공식 규정으로 바꿔 쓰거나 1차 답변을 덮어쓰지 않는다.";

  private final NewStartLlmClient newStartLlmClient;
  private final JipijigiResearchService researchService;

  public NewStartAiChatService(NewStartLlmClient newStartLlmClient, JipijigiResearchService researchService) {
    this.newStartLlmClient = newStartLlmClient;
    this.researchService = researchService;
  }

  /**
   * STEP 1 — 전자책 + 실제 사례 기반 1차 답변. 공식 자료는 이 단계 근거·출처에 섞지 않는다.
   * 이 응답이 STEP 2(공식 확인)를 기다리지 않고 먼저 사용자에게 표시된다.
   */
  public NewStartAiAnswer ask(String rawQuestion) {
    return run(rawQuestion, null, false);
  }

  /**
   * STEP 2 — 질문과 직접 관련된 현재 공식 자료를 별도로 조사해 1차 답변을 확인·보완하는 2차 답변.
   * 1차 답변({@code step1Answer})은 비교 입력일 뿐 수정하지 않으며, 이 결과는 1차 답변 아래에 추가된다.
   * 실패·timeout은 이 단계만의 실패이고 이미 표시된 1차 답변에는 영향이 없다.
   */
  public NewStartAiAnswer askOfficial(String rawQuestion, String step1Answer) {
    return run(rawQuestion, step1Answer == null ? "" : step1Answer.strip(), true);
  }

  private NewStartAiAnswer run(String rawQuestion, String step1Answer, boolean officialStep) {
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

    JipijigiQuestionIntent intent = JipijigiQuestionIntent.analyze(question);
    List<Evidence> ordered = officialStep ? officialEvidence(pack, intent) : caseEvidence(pack, intent);
    long tEvidence = System.nanoTime();

    if (officialStep && ordered.isEmpty()) {
      // 질문과 직접 연결된 공식 자료가 저장돼 있지 않다 — 모델이 공식 규정을 지어내지 않도록 LLM을 부르지 않고 정직하게 알린다.
      return new NewStartAiAnswer(true, noOfficialEvidenceMessage(pack), null, null, List.of());
    }

    String base = officialStep ? OFFICIAL_STEP_PROMPT : CASE_STEP_PROMPT;
    String evidenceContext = ordered.isEmpty() ? "" : buildEvidenceContext(ordered, officialStep);
    if (officialStep) {
      String agencies = agencyHints(pack);
      if (!agencies.isEmpty()) evidenceContext += "\n[추가 확인이 필요한 기관] " + agencies + "\n";
    }
    String intentBlock = officialStep ? officialScopeBlock(intent) : intent.promptBlock();
    String head = intentBlock.isEmpty() ? base : base + "\n\n" + intentBlock.stripTrailing();
    String systemPrompt = evidenceContext.isEmpty() ? head : head + "\n\n[NewStart 지식베이스 근거]\n" + evidenceContext;
    String userPrompt = officialStep ? officialUserPrompt(question, step1Answer) : question;
    long tPrompt = System.nanoTime();

    NewStartLlmCompletion completion =
        newStartLlmClient.complete(systemPrompt, userPrompt, MAX_ANSWER_CHARS);
    long tLlm = System.nanoTime();

    // 구간 실측(2026-09-28 latency 지시). 질문 원문·키·reasoning_content는 남기지 않고 길이와 ms만 남긴다.
    int promptChars = systemPrompt.length() + userPrompt.length();
    log.info("newstart ai chat timing step={} ragMs={} evidenceMs={} promptMs={} llmMs={} serviceMs={} "
            + "questionChars={} evidenceCount={} evidenceChars={} systemPromptChars={} promptChars={} "
            + "inputTokenEstimate={} historyMessages=0 answerChars={} model={} ok={}",
        officialStep ? "OFFICIAL" : "CASE",
        ms(tRag - tStart), ms(tEvidence - tRag), ms(tPrompt - tEvidence), ms(tLlm - tPrompt), ms(tLlm - tStart),
        question.length(), ordered.size(), evidenceChars(ordered), systemPrompt.length(), promptChars,
        promptChars / APPROX_CHARS_PER_TOKEN, completion.text().length(), completion.model(), completion.ok());

    if (!completion.ok()) {
      return NewStartAiAnswer.failed(completion.errorMessage(), toSources(ordered));
    }
    return new NewStartAiAnswer(true, sanitizeAnswer(completion.text(), evidenceContext), null, completion.model(),
        toSources(ordered));
  }

  /** STEP 2 범위 한정 블록 — 질문자의 큰 걱정·진행 단계에 직접 관련된 공식 규정만 확인하게 한다(신호가 없으면 빈 문자열). */
  static String officialScopeBlock(JipijigiQuestionIntent intent) {
    if (intent == null || (intent.concerns().isEmpty() && intent.stages().isEmpty())) return "";
    StringBuilder sb = new StringBuilder("[확인 범위 — 아래에 직접 관련된 공식 규정만 확인한다]\n");
    if (!intent.concerns().isEmpty()) sb.append("- 질문자의 큰 걱정: ").append(String.join(" / ", intent.concerns())).append('\n');
    if (!intent.stages().isEmpty()) sb.append("- 현재 진행 단계: ").append(String.join(" / ", intent.stages())).append('\n');
    return sb.toString();
  }

  /** STEP 2 입력: 질문 + 이미 표시된 1차 답변(수정 대상 아님, 비교 기준). 1차 답변은 길이만 제한한다. */
  static String officialUserPrompt(String question, String step1Answer) {
    String step1 = step1Answer == null ? "" : step1Answer;
    if (step1.length() > MAX_STEP1_CHARS) step1 = step1.substring(0, MAX_STEP1_CHARS) + "…";
    return "[질문]\n" + question
        + "\n\n[1차 답변 — 전자책·실제 사례 기반. 이미 사용자에게 표시됨. 다시 쓰지 말 것]\n"
        + (step1.isBlank() ? "(없음)" : step1)
        + "\n\n위 공식 자료 근거만으로 '현재 규정 확인'을 작성하라.";
  }

  /** 공식 자료를 찾지 못했을 때의 STEP 2 고정 안내 — 확인할 기관만 알려주고 규정 내용은 지어내지 않는다. */
  static String noOfficialEvidenceMessage(ResearchPack pack) {
    String agencies = agencyHints(pack);
    return "현재 질문과 직접 연결되는 공식 자료를 NewStart에 저장된 게시 자료에서 찾지 못했습니다. "
        + "위 답변은 전자책과 실제 사례를 바탕으로 한 것이므로 현재 기준은 "
        + (agencies.isEmpty() ? "새출발기금·신용회복위원회 등 관련 기관" : agencies)
        + "의 공식 안내에서 직접 확인해 주세요.";
  }

  /** V1 STEP5가 남긴 "수동 확인 필요" 기관 표기에서 기관명만 뽑는다(질문과 관련된 기관만 들어 있다). */
  static String agencyHints(ResearchPack pack) {
    if (pack == null || pack.getExtraAgencyHits() == null) return "";
    List<String> names = new ArrayList<>();
    for (Evidence e : pack.getExtraAgencyHits()) {
      if (e == null || e.getNote() == null) continue;
      String n = e.getNote().replace("수동 확인 필요", "").replace("(공식)", "").strip();
      if (!n.isEmpty() && !names.contains(n)) names.add(n);
    }
    return String.join(", ", names);
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
   * STEP 1 근거: 1순위 전자책·운영자 자료, 2순위 실제 사례(승인 카페 지식 → 축적 답변 → 실증 사례 → 카페 조회).
   * 공식 자료는 섞지 않는다. 같은 (refType, refId)는 1회만 실린다.
   *
   * <p>전자책이 컨텍스트를 다 채워 질문과 직접 관련된 실제 사례가 밀리지 않도록, 사례가 있으면 최소
   * {@value #CASE_RESERVE}칸을 사례에 보장한다(전자책 개수를 기계적으로 자르는 것이 아니라 사례 슬롯을 먼저 잡는다).
   */
  static List<Evidence> caseEvidence(ResearchPack pack) {
    return caseEvidence(pack, JipijigiQuestionIntent.none());
  }

  /**
   * STEP 1 근거(질문 의도 반영). 운영원칙 §2 순서 — <b>동일한 운영자 직접 경험 → 전자책</b> — 를 근거 목록에 반영한다:
   * 운영자 경험이 전자책보다 앞서고, 같은 부류 안에서는 질문 focus term 과의 관련도가 높은 자료가 앞선다(동률은 기존 순서 유지).
   */
  static List<Evidence> caseEvidence(ResearchPack pack, JipijigiQuestionIntent intent) {
    List<Evidence> ebooks = new ArrayList<>();
    Set<String> ebookSeen = new LinkedHashSet<>();
    addAll(ebooks, ebookSeen, filterClass(pack.getInternalHits(), SourceClass.OPERATOR_MATERIAL));
    ebooks.sort(Comparator.comparingInt((Evidence e) -> isOperatorExperience(e) ? 0 : 1)
        .thenComparingInt((Evidence e) -> -intent.relevance(e.getTitle(), e.getExcerpt())));

    List<Evidence> cases = new ArrayList<>();
    Set<String> caseSeen = new LinkedHashSet<>();
    // ADMIN 승인 ACTIVE 카페 지식은 원시 실증 조회보다 앞선 커레이션 등급(2026-09-30 GOLDEN-1).
    addAll(cases, caseSeen, filterClass(pack.getInternalHits(), SourceClass.BORROWER_CAFE_CASE));
    addAll(cases, caseSeen, filterClass(pack.getInternalHits(), SourceClass.CCRS_CAFE_CASE));
    addAll(cases, caseSeen, filterRef(pack.getInternalHits(), RefType.ANSWER_SESSION));
    addAll(cases, caseSeen, filterClass(pack.getInternalHits(), SourceClass.INTERNAL_CASE));
    addAll(cases, caseSeen, pack.getBorrowerHits());
    addAll(cases, caseSeen, pack.getCcrsHits());

    int caseReserve = Math.min(cases.size(), CASE_RESERVE);
    int ebookTake = Math.min(ebooks.size(), MAX_CONTEXT_EVIDENCE - caseReserve);
    int caseTake = Math.min(cases.size(), MAX_CONTEXT_EVIDENCE - ebookTake);

    List<Evidence> ordered = new ArrayList<>(ebooks.subList(0, ebookTake));
    ordered.addAll(cases.subList(0, caseTake));
    return List.copyOf(ordered);
  }

  /** STEP 2 근거: 현재 공식 자료만(게시된 공식 업데이트 + ACTIVE 공식 지식). 사례·전자책은 넣지 않는다. */
  static List<Evidence> officialEvidence(ResearchPack pack) {
    return officialEvidence(pack, JipijigiQuestionIntent.none());
  }

  /**
   * STEP 2 근거(질문 의도 반영). 질문에서 focus term 을 얻을 수 있으면 관련도 순으로 정렬하고 관련도 0(질문과 겹치는 용어 없음)인
   * 공식 자료는 싣지 않으며 {@value #MAX_OFFICIAL_FOCUSED}건까지만 쓴다 — 공식자료 검색 결과가 많다는 이유로 모두 싣지 않는다.
   * 질문에서 focus term 을 얻지 못하면(신호 없음) 기존처럼 최대 {@value #MAX_OFFICIAL_EVIDENCE}건. 공식자료를 새로 덧붙이지는 않는다.
   */
  static List<Evidence> officialEvidence(ResearchPack pack, JipijigiQuestionIntent intent) {
    List<Evidence> official = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    addAll(official, seen, pack.getOfficialHits());
    addAll(official, seen, filterClass(pack.getInternalHits(), SourceClass.OFFICIAL_RULE));
    if (intent == null || intent.focusTerms().isEmpty()) {
      return List.copyOf(official.subList(0, Math.min(official.size(), MAX_OFFICIAL_EVIDENCE)));
    }
    List<Evidence> focused = new ArrayList<>(official.stream()
        .filter(e -> intent.relevance(e.getTitle(), e.getExcerpt()) > 0).toList());
    focused.sort(Comparator.comparingInt((Evidence e) -> -intent.relevance(e.getTitle(), e.getExcerpt())));
    return List.copyOf(focused.subList(0, Math.min(focused.size(), MAX_OFFICIAL_FOCUSED)));
  }

  /** 운영자 직접 경험 자료인가(지식 항목 sourceType=OPERATOR_KNOWLEDGE). 전자책(EBOOK)과 구분한다. */
  static boolean isOperatorExperience(Evidence e) {
    return e != null && e.getNote() != null && e.getNote().contains("sourceType=OPERATOR_KNOWLEDGE");
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
    return buildEvidenceContext(ordered, false);
  }

  static String buildEvidenceContext(List<Evidence> ordered, boolean officialStep) {
    StringBuilder sb = new StringBuilder();
    sb.append(officialStep
        ? "(현재 공식 자료만. 1차 답변과 비교해 현재 규정으로 확인·보완한다.)\n"
        : "(전자책·운영자 경험·카페/실증 사례. 공식 자료가 아니므로 공식 안내처럼 쓰지 않는다. 각 자료의 성격 라벨을 답변에서도 구분한다.)\n");
    int i = 1;
    for (Evidence e : ordered) {
      sb.append(i++).append(". [").append(labelOf(e.getSourceClass())).append("] ")
          .append(e.getTitle().replace("\n", " ")).append("\n");
      if (e.getSourceClass() == SourceClass.OPERATOR_MATERIAL) {
        // 운영자 직접 경험과 전자책은 같은 라벨 묶음이지만 provenance 가 다르다 — 답변에서도 구분해 쓰게 한다.
        sb.append("   자료 성격: ").append(isOperatorExperience(e) ? "운영자 직접 경험" : "전자책").append("\n");
      }
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

  /**
   * 출처 목록: 전자책·운영자 자료가 앞, 그 뒤 사례(STEP 1) 또는 공식 자료만(STEP 2).
   * 사례가 있으면 최소 {@value #SOURCE_CASE_RESERVE}칸은 사례에 남겨 전자책이 출처 창을 다 채우지 않게 한다.
   */
  private static List<AnswerSource> toSources(List<Evidence> ordered) {
    List<Evidence> visible = ordered.stream().filter(e -> e.getRefType() != RefType.NONE).toList();
    List<Evidence> ebooks = visible.stream().filter(e -> e.getSourceClass() == SourceClass.OPERATOR_MATERIAL).toList();
    List<Evidence> others = visible.stream().filter(e -> e.getSourceClass() != SourceClass.OPERATOR_MATERIAL).toList();
    int otherReserve = Math.min(others.size(), SOURCE_CASE_RESERVE);
    int ebookTake = Math.min(ebooks.size(), MAX_SOURCES - otherReserve);
    int otherTake = Math.min(others.size(), MAX_SOURCES - ebookTake);
    List<AnswerSource> out = new ArrayList<>();
    for (Evidence e : ebooks.subList(0, ebookTake)) out.add(new AnswerSource(labelOf(e.getSourceClass()), e.getTitle(), e.getRefUrl()));
    for (Evidence e : others.subList(0, otherTake)) out.add(new AnswerSource(labelOf(e.getSourceClass()), e.getTitle(), e.getRefUrl()));
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
