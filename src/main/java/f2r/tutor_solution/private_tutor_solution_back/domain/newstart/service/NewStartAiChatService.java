package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
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
  /** 질문 길이 상한. 과대 프롬프트 남용을 막는 최소한의 가드다. */
  static final int MAX_QUESTION_CHARS = 2000;
  /** 답변 출력 상한. {@link NewStartLlmClient}가 응답에 적용한다. */
  static final int MAX_ANSWER_CHARS = 4000;
  /** 컨텍스트에 실리는 근거 총량 상한 — 프롬프트 폭주를 막는다. */
  static final int MAX_CONTEXT_EVIDENCE = 12;
  /** 공식 자료 상한. 이 값이 없으면 공식 8건+내부 공식 판정분이 12개를 모두 채워 사례가 못 들어온다. */
  static final int MAX_OFFICIAL_EVIDENCE = 8;
  /** 비공식 근거(운영·전자책·축적 답변·카페/실증 사례) 보장 슬롯 하한. */
  static final int MIN_SUPPORT_EVIDENCE = 4;
  /** 답변에 붙이는 출처 상한. */
  static final int MAX_SOURCES = 8;
  /** 근거 1건 요약 상한. */
  static final int MAX_EVIDENCE_CHARS = 400;

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
          + " 근거에 없는 내용은 근거가 없다고 밝히고 공식 기관 안내 확인을 권유한다.";

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
    try {
      pack = researchService.research(question);
    } catch (RuntimeException e) {
      // DB 장애로 조사를 못 하면 근거 없이 답하는 것보다 실패를 정직하게 알린다.
      return NewStartAiAnswer.failed("지식베이스 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }

    List<Evidence> ordered = orderedEvidence(pack);
    String systemPrompt = ordered.isEmpty()
        ? SYSTEM_PROMPT
        : SYSTEM_PROMPT + "\n\n[NewStart 지식베이스 근거]\n" + buildEvidenceContext(ordered);

    NewStartLlmCompletion completion =
        newStartLlmClient.complete(systemPrompt, question, MAX_ANSWER_CHARS);
    if (!completion.ok()) {
      return NewStartAiAnswer.failed(completion.errorMessage(), toSources(ordered));
    }
    return new NewStartAiAnswer(true, completion.text(), null, completion.model(), toSources(ordered));
  }

  /**
   * 근거 우선순위 조립: 1) 공식 자료 2) 승인 운영 지식/전자책 3) 승인 축적 답변 4) 카페·실증 사례.
   * 같은 (refType, refId) 근거는 먼저 온 우선순위로 1회만 실린다.
   *
   * <p>공식 자료는 {@value #MAX_OFFICIAL_EVIDENCE}건으로 막고, 비공식 근거(운영·전자책·축적 답변·
   * 카페/실증 사례)에는 최소 {@value #MIN_SUPPORT_EVIDENCE}슬롯을 보장한다. 공식이 12개를 채우면
   * 후순위 담보·임시안·납부 같은 카페 사례가 컨텍스트에서 통째로 사라지기 때문이다(2026-09-28 LIVE QA 실측).
   * 보장 슬롯은 컨텍스트뿐 아니라 답변에 보이는 출처 {@value #MAX_SOURCES}건 앞부분에서도 확보한다.
   */
  static List<Evidence> orderedEvidence(ResearchPack pack) {
    List<Evidence> official = new ArrayList<>();
    Set<String> officialSeen = new LinkedHashSet<>();
    // 1. 공식: STEP4 공식 검증_hits + 내부 판정된 OFFICIAL_RULE
    addAll(official, officialSeen, pack.getOfficialHits());
    addAll(official, officialSeen, filterClass(pack.getInternalHits(), SourceClass.OFFICIAL_RULE));

    List<Evidence> supporting = new ArrayList<>();
    Set<String> supportingSeen = new LinkedHashSet<>();
    // 2. 승인 운영 지식/전자책
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.OPERATOR_MATERIAL));
    // 3. 운영자 승인 축적 답변 세션
    addAll(supporting, supportingSeen, filterRef(pack.getInternalHits(), RefType.ANSWER_SESSION));
    // 4. 카페/실증 사례 (provenance 유지 — 공식으로 승격 금지)
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.INTERNAL_CASE));
    addAll(supporting, supportingSeen, pack.getBorrowerHits());
    addAll(supporting, supportingSeen, pack.getCcrsHits());
    // classifyKnowledge가 internalHits에 붙인 카페 분류 근거도 같은 우선순위로 포함한다.
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.BORROWER_CAFE_CASE));
    addAll(supporting, supportingSeen, filterClass(pack.getInternalHits(), SourceClass.CCRS_CAFE_CASE));

    int supportSlots = Math.min(MIN_SUPPORT_EVIDENCE, supporting.size());
    int contextOfficialSlots = Math.min(Math.min(official.size(), MAX_OFFICIAL_EVIDENCE),
        MAX_CONTEXT_EVIDENCE - supportSlots);
    // 출처는 ordered의 앞 MAX_SOURCES건이다. 공식이 그 앞을 다 채우면 회원 화면에 뜹니다.
    // 그래서 출처 상한 안에서도 사례 슬롯을 먼저 확보하고 공식 잔여로만 채운다.
    int sourceOfficialSlots = Math.min(Math.min(official.size(), MAX_SOURCES - supportSlots), contextOfficialSlots);

    List<Evidence> ordered = new ArrayList<>();
    ordered.addAll(official.subList(0, sourceOfficialSlots));
    ordered.addAll(supporting.subList(0, supportSlots));
    // 남은 컨텍스트 슬롯은 비공식 잔여 → 공식 잔여 순으로 메운다(사례를 뒤로 밀지 않는다).
    fillRemaining(ordered, supporting, supportSlots, supporting.size());
    fillRemaining(ordered, official, sourceOfficialSlots, contextOfficialSlots);
    return List.copyOf(ordered);
  }

  private static void fillRemaining(List<Evidence> ordered, List<Evidence> pool, int alreadyTaken, int poolCap) {
    int taken = alreadyTaken;
    for (int i = alreadyTaken; i < pool.size() && ordered.size() < MAX_CONTEXT_EVIDENCE && taken < poolCap; i++, taken++) {
      ordered.add(pool.get(i));
    }
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
    int i = 1;
    for (Evidence e : ordered) {
      sb.append(i++).append(". [").append(labelOf(e.getSourceClass())).append("] ")
          .append(e.getTitle().replace("\n", " ")).append("\n");
      String excerpt = e.getExcerpt() == null ? "" : e.getExcerpt().replace("\n", " ");
      if (excerpt.length() > MAX_EVIDENCE_CHARS) excerpt = excerpt.substring(0, MAX_EVIDENCE_CHARS) + "…";
      if (!excerpt.isBlank()) sb.append("   근거 요지: ").append(excerpt).append("\n");
      if (e.getRefUrl() != null && !e.getRefUrl().isBlank()) sb.append("   출처: ").append(e.getRefUrl()).append("\n");
    }
    return sb.toString();
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
    List<AnswerSource> out = new ArrayList<>();
    for (Evidence e : ordered) {
      if (out.size() >= MAX_SOURCES) break;
      if (e.getRefType() == RefType.NONE) continue;
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
