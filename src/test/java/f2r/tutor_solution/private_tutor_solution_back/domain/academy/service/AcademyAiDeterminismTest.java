package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.FieldChange;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyRequirementAnalyzer.AcademyRequirements;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 처리 결정성 테스트.
 *
 * <p>{@code Map.ofEntries} 순회 순서에 의존하지 않고,
 * 동일 입력에 항상 동일한 분석 결과가 나오는지 검증한다.
 */
@DisplayName("AI 처리 결정성 — 동일 입력은 동일 결과")
class AcademyAiDeterminismTest {

  private AcademyRequirementAnalyzer analyzer;
  private AcademyAiChatServiceImpl chatService;

  private final AcademyAiContext fixtureContext = new AcademyAiContext(
      "우리학원", "기존 소개문", List.of("영어"), List.of("MIDDLE"),
      "김원장", "대표원장", "기존 원장 소개", List.of("철학1", "철학2", "", ""), List.of(), null);

  /** 결정적 Fake: 프롬프트 해시 기반 고정 응답. */
  static class DeterministicFake implements AiProvider {
    @Override
    public AiCompletion complete(String prompt, AiTaskLimits limits) {
      return AiCompletion.ok("다듬어진 문구");
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public String name() {
      return "deterministic-fake";
    }
  }

  @BeforeEach
  void setUp() {
    analyzer = new AcademyRequirementAnalyzer();
    chatService = new AcademyAiChatServiceImpl(
        new DeterministicFake(), mock(AcademyAiContextService.class));
  }

  @Test
  @DisplayName("given 동일 생성 요청 20회 when 분석 then 항상 동일 프리셋·대상·톤")
  void analyze_sameInput20Times_sameResult() {
    String message = "압구정에 있는 중학생 전문 영어학원 홈페이지를 만들어 줘. "
        + "내신 대비가 강점이고, 학부모 상담을 강조해 줘. 깔끔하고 신뢰감 있게 구성해 줘.";

    List<AcademyRequirements> results = IntStream.range(0, 20)
        .mapToObj(i -> analyzer.analyze(message))
        .collect(Collectors.toList());

    AcademyRequirements first = results.get(0);
    for (AcademyRequirements r : results) {
      assertThat(r.preset()).isEqualTo(first.preset());
      assertThat(r.target()).isEqualTo(first.target());
      assertThat(r.subject()).isEqualTo(first.subject());
      assertThat(r.tone()).isEqualTo(first.tone());
      assertThat(r.strengths()).isEqualTo(first.strengths());
      assertThat(r.location()).isEqualTo(first.location());
    }
    // 기대값 고정
    assertThat(first.preset()).isEqualTo("ENGLISH");
    assertThat(first.target()).isEqualTo("중학생");
  }

  @Test
  @DisplayName("given 동일 수정 요청 20회 when 제안 then 항상 동일 필드 순서")
  void proposeChanges_sameInput20Times_sameFieldOrder() {
    String message = "소개글과 교육 철학을 함께 수정해줘";

    List<List<String>> results = IntStream.range(0, 20)
        .mapToObj(i -> chatService.proposeChanges(fixtureContext, message))
        .map(p -> p.changes().stream().map(FieldChange::key).collect(Collectors.toList()))
        .collect(Collectors.toList());

    List<String> first = results.get(0);
    assertThat(first).isNotEmpty();
    for (List<String> keys : results) {
      assertThat(keys).isEqualTo(first);
    }
  }

  @Test
  @DisplayName("given 톤 조절 요청 when 제안 then 항상 동일 톤 선택")
  void proposeChanges_toneRequest_sameTone() {
    String message = "소개글을 짧고 신뢰감 있게 바꿔줘";

    List<ChatProposal> results = IntStream.range(0, 20)
        .mapToObj(i -> chatService.proposeChanges(fixtureContext, message))
        .collect(Collectors.toList());

    String firstSummary = results.get(0).summary();
    for (ChatProposal p : results) {
      assertThat(p.summary()).isEqualTo(firstSummary);
    }
  }

  @Test
  @DisplayName("given 과목 경합 요청 when 분석 then 메시지 선행 키워드 우선")
  void analyze_competingSubjects_firstMentionWins() {
    // "수학"이 먼저 등장 → EXAM
    AcademyRequirements r1 =
        analyzer.analyze("수학학원인데 영어 회화도 가르치는 홈페이지 만들어줘");
    // "영어"가 먼저 등장 → ENGLISH
    AcademyRequirements r2 =
        analyzer.analyze("영어학원인데 수학도 가르치는 홈페이지 만들어줘");

    assertThat(r1.preset()).isEqualTo("EXAM");
    assertThat(r2.preset()).isEqualTo("ENGLISH");
  }
}
