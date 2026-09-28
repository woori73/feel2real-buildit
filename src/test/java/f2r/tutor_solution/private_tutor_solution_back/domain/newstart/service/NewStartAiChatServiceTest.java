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
    assertThat(client.capturedMaxChars).isPositive();
  }

  @Test
  @DisplayName("given ACTIVE 근거가 있을 when ask then 공식 라벨·본문·출처 URL이 system prompt에 실리고 sources로 노출된다")
  void ask_includesActiveEvidenceContext() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    research.pack = ResearchPack.builder().question("감면율")
        .internalHits(List.of())
        .officialHits(List.of(evidence(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "7",
            "[새출발기금] 감면율 기준", "https://nsf/official", "차상위는 최대 90%까지 가능하다")))
        .ccrsHits(List.of()).borrowerHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of())
        .externalSearched(false).build();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    NewStartAiChatService.NewStartAiAnswer answer = service.ask("감면율 기준이 궁금해요");

    assertThat(client.capturedSystem).contains("[NewStart 지식베이스 근거]");
    assertThat(client.capturedSystem).contains("[공식 자료]");
    assertThat(client.capturedSystem).contains("차상위는 최대 90%까지 가능하다");
    assertThat(client.capturedSystem).contains("https://nsf/official");
    assertThat(answer.sources()).hasSize(1);
    assertThat(answer.sources().get(0).kind()).isEqualTo("공식 자료");
  }

  @Test
  @DisplayName("given 공식 자료와 카페 사례가 모두 있을 when ask then 우선순위가 공식 > 운영자료 > 사례이고 카페 라벨이 보존된다")
  void ask_ordersOfficialBeforeCafeCases() {
    FakeLlmTransport client = new FakeLlmTransport();
    NewStartLlmClient llm = llmClient(client);
    FakeResearchService research = new FakeResearchService();
    research.pack = ResearchPack.builder().question("90% 되나요")
        .internalHits(List.of(
            evidence(SourceClass.OPERATOR_MATERIAL, RefType.KNOWLEDGE_ITEM, "2", "[전자책] 감면율 장", null, "감면율 산정 원리"),
            evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE, "5866", "90프로 탕감 사례", "https://cafe/5866", "후속 결과")))
        .officialHits(List.of(evidence(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE, "7",
            "공식 감면율 상한", "https://nsf", "최대 90%, 원칙 70%")))
        .ccrsHits(List.of()).borrowerHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of())
        .externalSearched(false).build();
    NewStartAiChatService service = new NewStartAiChatService(llm, research);

    service.ask("감면율이 90%까지 되나요?");

    String sys = client.capturedSystem;
    int official = sys.indexOf("[공식 자료]");
    int ebook = sys.indexOf("[운영자·전자책 자료]");
    int cafe = sys.indexOf("[부실차주 카페 사례]");
    assertThat(official).isGreaterThan(-1);
    assertThat(ebook).isGreaterThan(official);
    assertThat(cafe).isGreaterThan(ebook);
    assertThat(sys).contains("카페 경험담이나 근거 없는 자료를 공식 정책처럼 말하지 않는다");
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
}
