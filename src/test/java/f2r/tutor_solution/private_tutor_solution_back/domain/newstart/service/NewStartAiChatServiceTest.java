package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NewStartAiChatService — given/when/then 단위테스트. 실제 LLM 호출 대신 {@link FakeLlmTransport},
 * DB 대신 {@link FakeResearchService}로 retrieval→context→DIRECT LLM 위임 흐름과 입력 가드를 검증한다.
 * 근거 조립(order·라벨·컨텍스트) 계약은 LLM transport 교체 전과 동일하게 유지된다.
 */
@DisplayName("NewStartAiChatService — 지식 retrieval → 근거 컨텍스트 → NewStart DIRECT LLM 위임")
class NewStartAiChatServiceTest {

  /** 네트워크 없이 complete() 인자만 캡처하는 fake transport. primary로만 붙는다. */
  private static class FakeLlmTransport implements NewStartLlmTransport {
    boolean wasCalled;
    String capturedSystem;
    String capturedPrompt;
    int capturedMaxChars;
    NewStartLlmCompletion stubbed = NewStartLlmCompletion.success(
        "답변입니다.", "nvidia", "nvidia/nemotron-3.5-lightning-30b-a3b");

    @Override
    public String name() {
      return "fake-primary";
    }

    @Override
    public NewStartLlmCompletion complete(String systemPrompt, String prompt, int maxOutputChars) {
      this.wasCalled = true;
      this.capturedSystem = systemPrompt;
      this.capturedPrompt = prompt;
      this.capturedMaxChars = maxOutputChars;
      return stubbed;
    }
  }

  /** primary-only 클라이언트: 이 테스트들의 stub은 항상 fallbackEligible=false라 fallback은 0회다. */
  private static NewStartLlmClient llmClient(FakeLlmTransport primary) {
    return new NewStartLlmClient(primary, new FakeLlmTransport());
  }

  /** DB 없이 research() 결과만 캡처·조작하는 fake 지피지기 조사 사다리. */
  private static class FakeResearchService extends JipijigiResearchService {
    boolean wasResearched;
    String capturedQuestion;
    ResearchPack pack = emptyPack("");
    RuntimeException failure;

    FakeResearchService() {
      super(null, null, null, null);
    }

    @Override
    public ResearchPack research(String questionText) {
      this.wasResearched = true;
      this.capturedQuestion = questionText;
      if (failure != null) throw failure;
      return pack;
    }
  }

  private static ResearchPack emptyPack(String question) {
    return ResearchPack.builder().question(question)
        .internalHits(List.of()).ccrsHits(List.of()).borrowerHits(List.of())
        .officialHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of())
        .externalSearched(false).build();
  }

  private static Evidence evidence(SourceClass cls, RefType type, String id, String title, String url, String excerpt) {
    return Evidence.builder().stepNo(1).sourceClass(cls).refType(type).refId(id)
        .refUrl(url).title(title).excerpt(excerpt).note(null).build();
  }


  @Test
  @DisplayName("given 정상 질문 when ask then 조사 사다리를 통과하고 DIRECT LLM에 system prompt와 함께 위임한다")
  void ask_delegatesToDirectLlm() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("새출발기금이 무엇인가요?");

    assertThat(research.wasResearched).isTrue();
    assertThat(research.capturedQuestion).isEqualTo("새출발기금이 무엇인가요?");
    assertThat(answer.ok()).isTrue();
    assertThat(answer.answer()).isEqualTo("답변입니다.");
    assertThat(answer.model()).isEqualTo("nvidia/nemotron-3.5-lightning-30b-a3b");
    assertThat(client.wasCalled).isTrue();
    assertThat(client.capturedPrompt).isEqualTo("새출발기금이 무엇인가요?");
    assertThat(client.capturedSystem).contains("새출발기금");
    // LOCAL E2E(2026-09-28)에서 Nemotron이 사고 과정을 content로 흘린 사례 → 출력 계약으로 막는다
    assertThat(client.capturedSystem).contains("사용자에게 바로 보여도 되는 최종 문장만 한국어로");
    // LOCAL E2E(2026-09-29)에서 Nemotron이 [KB2 공식]형 인용표를 자체 생성 → 금지 지시
    assertThat(client.capturedSystem).contains("대괄호 번호·키워드 인용표를 쓰지 말고");
    assertThat(client.capturedSystem).contains("'[KB1]', '[KB2 공식]'");
    assertThat(client.capturedMaxChars).isPositive();
  }

  @Test
  @DisplayName("given 근거가 0건 when ask then 컨텍스트 없이 기존 system prompt로 위임하고 sources는 빈다")
  void ask_withoutEvidenceHasNoContextBlock() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("전혀 없는 주제 질문");

    // system prompt 본문이 마커를 언급하므로, 근거 블록 자체가 붙지 않은 것을 확인한다.
    assertThat(client.capturedSystem).doesNotContain("\n\n[NewStart 지식베이스 근거]\n");
    assertThat(answer.sources()).isEmpty();
  }

  @Test
  @DisplayName("given 조사 DB 실패 when ask then LLM을 타지 않고 정직한 실패를 돌려준다")
  void ask_researchFailureDoesNotAskModelWithoutGrounding() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    research.failure = new IllegalStateException("db down");
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("질문");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("지식베이스 조회에 실패");
    assertThat(client.wasCalled).isFalse();
  }

  @Test
  @DisplayName("given 빈 질문 when ask then 조사도 LLM도 타지 않는다")
  void ask_rejectsBlankQuestion() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("   ");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("질문");
    assertThat(research.wasResearched).isFalse();
    assertThat(client.wasCalled).isFalse();
  }

  @Test
  @DisplayName("given 길이 초과 질문 when ask then 조사도 LLM도 타지 않는다")
  void ask_rejectsOverlongQuestion() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("가".repeat(2001));

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("너무 깁니다");
    assertThat(client.wasCalled).isFalse();
  }

  @Test
  @DisplayName("given LLM 실패 when ask then 근거 출처 목록은 유지하며 안내 문구를 돌려준다")
  void ask_propagatesLlmFailureMessage() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    research.pack = ResearchPack.builder().question("질문")
        .internalHits(List.of(evidence(SourceClass.INTERNAL_CASE, RefType.EMPIRICAL_CASE, "5866",
            "90% 탕감 사례", "https://cafe/5866", "체결 완료")))
        .officialHits(List.of()).ccrsHits(List.of()).borrowerHits(List.of()).extraAgencyHits(List.of())
        .gapNotes(List.of()).externalSearched(false).build();
    client.stubbed = NewStartLlmCompletion.failed("AI 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.", false);
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("질문");

    assertThat(answer.ok()).isFalse();
    assertThat(answer.errorMessage()).contains("잠시 후 다시 시도");
    assertThat(answer.answer()).isEmpty();
    assertThat(answer.sources()).hasSize(1);
    assertThat(answer.sources().get(0).kind()).isEqualTo("실증 사례(DB)");
  }

  @Test
  @DisplayName("given 앞뒤 공백 질문 when ask then strip된 질문이 조사와 LLM 양쪽에 전달된다")
  void ask_stripsQuestion() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    service.ask("  질문  ");

    assertThat(client.capturedPrompt).isEqualTo("질문");
    assertThat(research.capturedQuestion).isEqualTo("질문");
  }

  @Test
  @DisplayName("given 모델이 만든 [KB2 공식] 인용표와 근거에 없는 전화번호 when sanitize then 인용표·미근거 번호는 제거, 근거 속 번호는 유지")
  void sanitizeAnswer_removesInventedCitationsAndUngroundedPhones() {
    String ctx = "근거 요지: 신용회복위원회 FAX 02-2169-7028";
    String raw = "공식 안내에 따르면 가능합니다 [KB2 공식]. 문의는 02-1234-5678 또는 02-2169-7028 로 하세요.";

    String out = NewStartAiChatService.sanitizeAnswer(raw, ctx);

    assertThat(out).doesNotContain("[KB2");
    assertThat(out).doesNotContain("02-1234-5678");
    assertThat(out).contains("02-2169-7028");
  }

  @Test
  @DisplayName("given 근거 note에 작성 시점 when 컨텍스트 조립 then 시점이 노출된다")
  void buildEvidenceContext_showsTime() {
    Evidence e = Evidence.builder().stepNo(3).sourceClass(SourceClass.BORROWER_CAFE_CASE).refType(RefType.EMPIRICAL_CASE)
        .refId("1").title("사례").excerpt("내용").note("카페 실증 사례, 작성 2026-08-11").build();

    assertThat(NewStartAiChatService.buildEvidenceContext(List.of(e))).contains("시점: 2026-08-11");
  }

  // ─────────────── 2단계 답변 구조 (STEP 1 = 전자책+실제 사례, STEP 2 = 현재 공식자료) ───────────────

  private static ResearchPack mixedPack() {
    return ResearchPack.builder().question("후순위 담보대출 기한이익상실")
        .internalHits(List.of(
            evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "e1", "[전자책] EBK-9-1 부동산담보 처리 기준", null, "후순위 담보 접수 방식"),
            evidence(SourceClass.OFFICIAL_RULE, RefType.KNOWLEDGE_ITEM, "d1", "[공식] 새출발기금 담보채무 기준", "https://nsf/d1", "담보채무는 캠코 인수")))
        .borrowerHits(List.of(evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "6037",
            "사업자 후순위 담보대출 문의드립니다.", "https://cafe.naver.com/newhopero/6037", "기한이익상실 전 상담")))
        .officialHits(List.of(evidence(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "u1",
            "[새출발기금] 부실우려 담보채무 안내", "https://nsf/u1", "2026 기준 공식 안내")))
        .ccrsHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of()).externalSearched(false).build();
  }

  @Test
  @DisplayName("A. given 전자책·사례·공식이 모두 있을 when STEP 1(ask) then 전자책+실제 사례만 쓰고 공식 자료는 컨텍스트·출처에 없다")
  void step1_usesEbookAndCasesOnly() {
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = mixedPack();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);

    NewStartAiChatService.NewStartAiAnswer a = service.ask("후순위 담보대출 기한이익상실 통보를 받았는데 어떻게 해야 하나?");

    String sys = client.capturedSystem;
    assertThat(sys).contains("[운영자·전자책 자료]").contains("[부실차주 카페 사례]");
    assertThat(sys).doesNotContain("[공식 자료]");
    assertThat(sys).doesNotContain("담보채무는 캠코 인수").doesNotContain("2026 기준 공식 안내");
    assertThat(sys.indexOf("[운영자·전자책 자료]")).isLessThan(sys.indexOf("[부실차주 카페 사례]"));
    assertThat(sys).contains(NewStartAiChatService.CASE_STEP_PROMPT);
    assertThat(sys).contains("공식 안내에 따르면'·'공식 자료'라고 쓰지 않고");
    assertThat(a.sources()).extracting(NewStartAiChatService.AnswerSource::kind)
        .containsExactly("운영자·전자책 자료", "부실차주 카페 사례")
        .doesNotContain("공식 자료");
  }

  @Test
  @DisplayName("A2. given 전자책이 12칸을 다 채울 만큼 많고 직접 관련 사례가 있을 when STEP 1 then 사례가 컨텍스트·출처에서 밀리지 않는다")
  void step1_ebooksDoNotCrowdOutDirectCases() {
    List<Evidence> ebooks = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ebooks.add(evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "e" + i, "[전자책] EBK-" + i, null, "담보"));
    }
    ResearchPack pack = ResearchPack.builder().question("q").internalHits(ebooks)
        .borrowerHits(List.of(
            evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "6037", "후순위 담보 사례1", "https://cafe/6037", "직접 관련"),
            evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "6038", "후순위 담보 사례2", "https://cafe/6038", "직접 관련")))
        .officialHits(List.of()).ccrsHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of())
        .externalSearched(false).build();

    List<Evidence> ordered = NewStartAiChatService.caseEvidence(pack);

    assertThat(ordered).hasSizeLessThanOrEqualTo(NewStartAiChatService.MAX_CONTEXT_EVIDENCE);
    assertThat(ordered).extracting(Evidence::getRefId).contains("6037", "6038");
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = pack;
    NewStartAiChatService.NewStartAiAnswer a = new NewStartAiChatService(llmClient(client), research).ask("후순위 담보 질문");
    assertThat(a.sources()).extracting(NewStartAiChatService.AnswerSource::title)
        .contains("후순위 담보 사례1", "후순위 담보 사례2");
    assertThat(a.sources()).hasSizeLessThanOrEqualTo(NewStartAiChatService.MAX_SOURCES);
  }

  @Test
  @DisplayName("A3. given ACTIVE 카페 지식과 원시 실증 사례 공존 when STEP 1 then 카페 지식이 실증 조회보다 앞서고 출처 창 안에 든다 (GOLDEN-1 회귀)")
  void step1_keepsApprovedCafeKnowledgeAheadOfRawCases() {
    List<Evidence> internal = new java.util.ArrayList<>();
    internal.add(evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "118", "[운영자 자료] 2024 기준선", "", "폐업사실증명만 제출"));
    internal.add(evidence(SourceClass.BORROWER_CAFE_CASE, RefType.KNOWLEDGE_ITEM, "119", "[카페 사례] 2026-08 보완요구", "", "임대차서류 보완요구 관찰"));
    List<Evidence> empirical = List.of(
        evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "5947", "감면율 사례1", "https://cafe/5947", "감면"),
        evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "6001", "감면율 사례2", "https://cafe/6001", "감면"));
    ResearchPack pack = ResearchPack.builder().question("폐업 보증금").internalHits(internal).borrowerHits(empirical)
        .officialHits(List.of()).ccrsHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of()).externalSearched(false).build();

    List<Evidence> ordered = NewStartAiChatService.caseEvidence(pack);

    List<String> ids = ordered.stream().map(Evidence::getRefId).toList();
    assertThat(ids.indexOf("119")).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf("5947"));
  }

  @Test
  @DisplayName("B. given STEP 1 성공 후 STEP 2 when 호출 순서 then STEP 1 응답이 먼저 반환되고 STEP 2는 그 다음 별도 호출이다")
  void step1_returnsBeforeStep2_separateCalls() {
    FakeLlmTransport client = new FakeLlmTransport();
    List<String> order = new java.util.ArrayList<>();
    FakeResearchService research = new FakeResearchService() {
      @Override
      public ResearchPack research(String q) {
        order.add("research");
        return super.research(q);
      }
    };
    research.pack = mixedPack();
    NewStartLlmTransport recording = new NewStartLlmTransport() {
      @Override public String name() { return "rec"; }
      @Override public NewStartLlmCompletion complete(String sys, String prompt, int max) {
        order.add(sys.contains("[1차 답변 — 전자책·실제 사례]") ? "LLM-STEP1" : "LLM-STEP2");
        return NewStartLlmCompletion.success(sys.contains("[1차 답변 — 전자책·실제 사례]") ? "1차 답변 본문" : "2차 답변 본문",
            "nvidia", "m");
      }
    };
    NewStartAiChatService service = new NewStartAiChatService(new NewStartLlmClient(recording, new FakeLlmTransport()), research);

    NewStartAiChatService.NewStartAiAnswer step1 = service.ask("질문");
    order.add("STEP1-DELIVERED");
    NewStartAiChatService.NewStartAiAnswer step2 = service.askOfficial("질문", step1.answer());

    assertThat(order).containsExactly("research", "LLM-STEP1", "STEP1-DELIVERED", "research", "LLM-STEP2");
    assertThat(step1.answer()).isEqualTo("1차 답변 본문");
    assertThat(step2.answer()).isEqualTo("2차 답변 본문");
  }

  @Test
  @DisplayName("C. given STEP 2 when askOfficial then 공식 자료만 근거이고 전자책·사례는 근거에 없으며 1차 답변이 비교 입력으로 전달된다")
  void step2_usesOfficialEvidenceOnly_andReceivesStep1() {
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = mixedPack();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);

    NewStartAiChatService.NewStartAiAnswer a = service.askOfficial("후순위 담보대출 기한이익상실", "전자책에서는 이렇게 진행됐습니다.");

    assertThat(client.capturedSystem).contains(NewStartAiChatService.OFFICIAL_STEP_PROMPT);
    assertThat(client.capturedSystem).contains("[공식 자료]").contains("2026 기준 공식 안내").contains("담보채무는 캠코 인수");
    assertThat(client.capturedSystem).doesNotContain("[운영자·전자책 자료]").doesNotContain("[부실차주 카페 사례]");
    assertThat(client.capturedPrompt).contains("전자책에서는 이렇게 진행됐습니다.").contains("다시 쓰지 말 것");
    assertThat(a.sources()).extracting(NewStartAiChatService.AnswerSource::kind).containsOnly("공식 자료");
  }

  @Test
  @DisplayName("D. given STEP 2 프롬프트 when 확인 then 1차 답변 재작성·삭제 금지와 차이·시점 비교 지시가 있다")
  void step2_promptForbidsOverwriteAndComparesTimeline() {
    assertThat(NewStartAiChatService.OFFICIAL_STEP_PROMPT)
        .contains("반복하거나 삭제·정정하지 말고")
        .contains("1차 답변(사례·전자책)과 현재 기준이 다른 점")
        .contains("시점 차이")
        .contains("사례를 틀렸다고 단정하지 않고");
  }

  @Test
  @DisplayName("E. given STEP 2 LLM timeout when askOfficial then STEP 2만 실패 응답이고 이미 받은 STEP 1 응답 객체는 그대로 유지된다")
  void step2_timeoutKeepsStep1() {
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = mixedPack();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);
    NewStartAiChatService.NewStartAiAnswer step1 = service.ask("질문");
    String step1Text = step1.answer();
    List<NewStartAiChatService.AnswerSource> step1Sources = step1.sources();

    client.stubbed = NewStartLlmCompletion.failed("AI 서비스 응답이 지연되고 있습니다.", false);
    NewStartAiChatService.NewStartAiAnswer step2 = service.askOfficial("질문", step1Text);

    assertThat(step2.ok()).isFalse();
    assertThat(step1.ok()).isTrue();
    assertThat(step1.answer()).isEqualTo(step1Text).isEqualTo("답변입니다.");
    assertThat(step1.sources()).isEqualTo(step1Sources).isNotEmpty();
  }

  @Test
  @DisplayName("E2. given 공식 자료가 없을 때 STEP 2 then LLM을 부르지 않고(공식 규정을 지어내지 않고) 확인할 기관만 안내한다")
  void step2_withoutOfficialEvidence_doesNotCallLlm() {
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = ResearchPack.builder().question("q")
        .internalHits(List.of(evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "e1", "[전자책] x", null, "y")))
        .officialHits(List.of()).ccrsHits(List.of()).borrowerHits(List.of())
        .extraAgencyHits(List.of(Evidence.builder().stepNo(5).sourceClass(SourceClass.OTHER).refType(RefType.NONE)
            .title("추가 공식기관 확인 필요").note("대한민국 법원 전자소송포털 수동 확인 필요").build()))
        .gapNotes(List.of()).externalSearched(false).build();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);

    NewStartAiChatService.NewStartAiAnswer a = service.askOfficial("지급명령이 왔는데", "1차 답변");

    assertThat(a.ok()).isTrue();
    assertThat(client.wasCalled).isFalse();
    assertThat(a.answer()).contains("공식 자료를 NewStart에 저장된 게시 자료에서 찾지 못했습니다").contains("대한민국 법원 전자소송포털");
    assertThat(a.sources()).isEmpty();
  }

  @Test
  @DisplayName("F. given 사례와 현재 공식 기준이 다를 수 있을 when 두 단계 then 각 단계가 자기 근거만 쓰고 서로 삭제하지 않는다(차이는 STEP 2가 표시)")
  void step1AndStep2_keepOwnEvidence_differenceShownInStep2() {
    FakeLlmTransport client = new FakeLlmTransport();
    FakeResearchService research = new FakeResearchService();
    research.pack = mixedPack();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);

    service.ask("q");
    String step1Sys = client.capturedSystem;
    service.askOfficial("q", "당시 사례에서는 통보 후에도 접수됐다");
    String step2Sys = client.capturedSystem;

    assertThat(step1Sys).contains("사업자 후순위 담보대출 문의드립니다.");
    assertThat(step2Sys).doesNotContain("사업자 후순위 담보대출 문의드립니다.");
    assertThat(step2Sys).contains("시점 차이");
    assertThat(client.capturedPrompt).contains("당시 사례에서는 통보 후에도 접수됐다");
  }

  @Test
  @DisplayName("G. given 모델 출력에 근거 없는 전화번호·공식 출처표 when 두 단계 then 제거되고 근거 속 번호만 남는다")
  void bothSteps_doNotEmitUngroundedPhonesOrCitations() {
    FakeLlmTransport client = new FakeLlmTransport();
    client.stubbed = NewStartLlmCompletion.success("문의 02-1234-5678 로 하세요 [KB2 공식]. 팩스 02-2169-7028", "nvidia", "m");
    FakeResearchService research = new FakeResearchService();
    research.pack = ResearchPack.builder().question("q").internalHits(List.of(
            evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "e1", "[전자책] 팩스", null, "신용회복위원회 FAX 02-2169-7028")))
        .officialHits(List.of(evidence(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "u1", "공식", "https://x", "안내 02-2169-7028")))
        .borrowerHits(List.of()).ccrsHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of()).externalSearched(false).build();
    NewStartAiChatService service = new NewStartAiChatService(llmClient(client), research);

    for (String out : new String[] {service.ask("q").answer(), service.askOfficial("q", "1차").answer()}) {
      assertThat(out).doesNotContain("02-1234-5678").doesNotContain("[KB2").contains("02-2169-7028");
    }
  }

  @Test
  @DisplayName("H. given 두 단계 프롬프트 when 확인 then 영문 꼬리·번호 인용표·사고과정 금지가 공통이고 전자책을 공식으로 부르지 않도록 STEP 1이 막는다")
  void prompts_forbidEnglishTailAndOfficialMislabel() {
    for (String p : new String[] {NewStartAiChatService.CASE_STEP_PROMPT, NewStartAiChatService.OFFICIAL_STEP_PROMPT}) {
      assertThat(p).contains("영문 문구·메모·'official guidance' 같은 꼬리를 붙이지 않는다");
      assertThat(p).contains("근거에 없는 전화번호·URL·금액·기한·날짜를 만들어내지 않는다");
      assertThat(p).contains("재산 은닉, 허위 거래, 가장행위, 심사 회피");
    }
    assertThat(NewStartAiChatService.CASE_STEP_PROMPT)
        .contains("전자책은 '전자책에서는'").contains("공식 자료가 아니다").contains("현재 규정은 다음 단계에서 따로 확인한다");
  }
}
