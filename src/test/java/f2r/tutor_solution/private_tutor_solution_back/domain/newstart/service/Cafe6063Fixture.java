package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import java.util.List;

/**
 * 실제 카페 게시글 #6063 회귀 fixture(조사 사다리 결과 pack).
 *
 * <p><b>출처 주의</b>: 원 게시글 본문과 개선 전 Agent STEP 1·2 답변 원문은 이 저장소에 없고(운영 DB/카페에만 있음) 두 차례 지시에서도
 * 본문으로 전달되지 않았다(운영자 답변은 핵심 요지만 전달됨). 그래서 {@link #QUESTION}은 사용자가 전달한 사건 요약(핵심 걱정·진행 단계·운영자 답변 요지)으로
 * 만든 <b>재구성 질문</b>이며 원문 그대로가 아니다. 운영자 실제 답변 요지와 개선 전 STEP 2가 길게 나열했던 공식규정 주제는
 * 사용자 지시문에 적힌 그대로 옮겼다. 원문이 확보되면 {@code newstart/regression/cafe_6063.json}과 이 클래스의 상수를 교체한다.
 * 이 pack 의 제목·요지는 retrieval 결과를 흉내 낸 테스트 데이터이며 운영 DB 데이터가 아니다.
 */
final class Cafe6063Fixture {

  /** 재구성 질문 — 원문 아님(위 주의). */
  static final String QUESTION =
      "회생 폐지 후 원장초기화를 하고 새출발기금(부실차주)을 신청하려고 준비 중입니다. 대부업체 채무도 있는데 대부기관도 새출발기금 협약기관인지,"
          + " 안 되면 어떻게 해야 하는지 궁금합니다. 그런데 준비하는 동안 추심이나 압류가 들어와서 집까지 문제가 생길까 봐 너무 걱정됩니다."
          + " 어떻게 해야 할지 막막합니다.";

  private Cafe6063Fixture() {}

  private static Evidence ev(SourceClass cls, RefType type, String id, String title, String url, String excerpt, String note) {
    return Evidence.builder().stepNo(1).sourceClass(cls).refType(type).refId(id).refUrl(url).title(title)
        .excerpt(excerpt).note(note).build();
  }

  /** 개선 전/후 공통 입력: 전자책 → 운영자 경험 순으로 섞여 들어오는 내부 근거 + 카페 사례 + (질문과 무관한 것이 섞인) 공식 자료. */
  static ResearchPack pack() {
    List<Evidence> internal = List.of(
        // 전자책이 먼저 들어와 있다(검색은 최신순/키워드 일치순이라 운영자 경험이 뒤에 올 수 있다).
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "eb1", "[전자책] 새출발기금 신청 전체 절차", null,
            "신청 접수, 심사, 채무조정 확정 순서로 진행된다", "sourceType=EBOOK, authority=전자책"),
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "eb2", "[전자책] 채무 감면율 계산 기초", null,
            "감면율은 상환능력에 따라 달라진다", "sourceType=EBOOK, authority=전자책"),
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "eb3", "[전자책] 채무 종류별 처리 개요", null,
            "금융기관 채무와 대부업 채무는 처리 경로가 다를 수 있다", "sourceType=EBOOK, authority=전자책"),
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "op1", "[운영자 경험] 원장초기화 후 Credit4U 장기연체 등록 확인 → 부실차주 신청", null,
            "운영자 실제 경험상 원장초기화는 메일을 보낸 뒤 보통 3일 안에 처리됐다. 이후 Credit4U에서 장기연체 등록을 확인하고, 확인 후 부실차주를 신청했다.",
            "sourceType=OPERATOR_KNOWLEDGE, authority=운영자 직접 경험, 자료시점=2026-09-20"),
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "op2", "[운영자 경험] 대부기관 협약 여부 확인과 비협약 채무 처리", null,
            "대부기관도 새출발기금 협약기관인지 먼저 확인한다. 협약기관 채권은 새출발기금에서 매입되는지 확인하고, 비협약기관 채무는 부실차주 신청 후 신복위 개인워크아웃을 검토한다.",
            "sourceType=OPERATOR_KNOWLEDGE, authority=운영자 직접 경험, 자료시점=2026-09-20"),
        ev(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "op3", "[운영자 경험] 준비 중 추심·압류 걱정에 대한 운영자 판단", null,
            "이 질문자에 대한 운영자의 실제 판단: \"현 상황에서는 집 압류 걱정할 필요없습니다.\" 새출발 진행 중 실제 추심·압류 문제가 생기면 금감원 민원으로 대응한다.",
            "sourceType=OPERATOR_KNOWLEDGE, authority=운영자 직접 경험, 자료시점=2026-09-20"));

    List<Evidence> borrower = List.of(
        ev(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "5884", "원장초기화 메일 보내고 3일 걸렸습니다", "https://cafe.naver.com/newhopero/5884",
            "원장초기화 신청 후 장기연체 등록 확인까지 며칠 걸렸는지", "카페 실증 사례, 작성 2026-08-20"));

    // 공식 자료 후보: 질문과 직접 관련된 것 2건 + 개선 전 STEP 2 가 길게 나열했던 무관 주제 7건.
    List<Evidence> official = List.of(
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of1", "[새출발기금] 공공정보 등록 해제 안내", "https://nsf/of1", "공공정보 등록 해제 절차", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of2", "[새출발기금] 조기상환 시 추가감면 안내", "https://nsf/of2", "조기상환하면 추가감면", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of3", "[새출발기금] 가상자산 보유 시 채무조정 기준", "https://nsf/of3", "가상자산은 재산으로 반영", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of4", "[새출발기금] 비상장주식 평가 기준", "https://nsf/of4", "비상장주식 평가", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of5", "[새출발기금] 고소득자 지원 제외 기준", "https://nsf/of5", "고소득자 제외", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of6", "[새출발기금] 사해행위 해당 시 불이익", "https://nsf/of6", "사해행위", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of7", "[새출발기금] 변제가능률 산정 방식", "https://nsf/of7", "변제가능률 산정", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of8", "[새출발기금] 협약기관 채무 매입 및 협약 금융회사 안내", "https://nsf/of8",
            "협약기관(대부업 포함) 채권은 새출발기금이 매입해 채무조정한다. 비협약 채무는 대상이 아니다", "공식"),
        ev(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "of9", "[금융감독원] 불법·부당 채권추심 피해 민원 안내", "https://fss/of9",
            "채권추심 관련 피해는 금융감독원에 민원을 제기할 수 있다", "공식"));

    Evidence agency = Evidence.builder().stepNo(5).sourceClass(SourceClass.OTHER).refType(RefType.NONE)
        .title("추가 공식기관 확인 필요").note("금융감독원(공식) 수동 확인 필요").build();

    return ResearchPack.builder().question(QUESTION).internalHits(internal).ccrsHits(List.of()).borrowerHits(borrower)
        .officialHits(official).extraAgencyHits(List.of(agency)).gapNotes(List.of()).externalSearched(false).build();
  }

  /** DB 없이 고정 pack 을 돌려주는 조사 사다리. */
  static final class FixedResearch extends JipijigiResearchService {
    private final ResearchPack pack;

    FixedResearch(ResearchPack pack) {
      super(null, null, null, null);
      this.pack = pack;
    }

    @Override
    public ResearchPack research(String questionText) {
      return pack;
    }
  }

  /** 모델 호출 대신 system/user prompt 를 캡처해 모델에 실제로 들어가는 근거·지시를 비교 가능하게 한다. */
  static final class CapturingTransport implements NewStartLlmTransport {
    String system;
    String prompt;

    @Override
    public String name() {
      return "capture";
    }

    @Override
    public NewStartLlmCompletion complete(String systemPrompt, String userPrompt, int maxOutputChars) {
      this.system = systemPrompt;
      this.prompt = userPrompt;
      return NewStartLlmCompletion.success("(캡처 — 모델 호출 없음)", "capture", "capture");
    }
  }

  static NewStartAiChatService.NewStartAiAnswer[] run(CapturingTransport t1, CapturingTransport t2) {
    NewStartAiChatService s1 = new NewStartAiChatService(new NewStartLlmClient(t1, new CapturingTransport()), new FixedResearch(pack()));
    NewStartAiChatService s2 = new NewStartAiChatService(new NewStartLlmClient(t2, new CapturingTransport()), new FixedResearch(pack()));
    return new NewStartAiChatService.NewStartAiAnswer[] {s1.ask(QUESTION), s2.askOfficial(QUESTION, "(1차 답변 — 캡처 입력)")};
  }
}
