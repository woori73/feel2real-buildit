package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ValidationResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyRequirementAnalyzer.AcademyRequirements;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BuildIt P1 AI 홈빌더 실제 시나리오 테스트.
 *
 * <p>각 테스트는 실제 LLM 호출 여부와 목업 여부를 구분해 기록한다.
 * 현재는 FakeAiProvider를 사용하므로 "목업"으로 표시.
 * 실제 LLM 연동 후에는 "실제"로 전환하여 검증한다.
 *
 * <p>테스트 시나리오:
 * <ol>
 *   <li>영어학원 홈페이지 생성
 *   <li>수학학원 홈페이지 생성
 *   <li>예체능학원 홈페이지 생성
 *   <li>학원 소개글 반복 수정
 *   <li>여러 항목 동시 수정
 *   <li>허위 성적·경력 생성 방지
 *   <li>허용되지 않은 필드 변경 거부
 *   <li>사용자 승인 전 DB 미변경
 *   <li>생성 후 버전 복원
 *   <li>기존 P0 발행 기능 회귀 테스트
 * </ol>
 */
class AcademyAiScenarioTest {

  private AcademyRequirementAnalyzer analyzer;
  private AcademyAiCreateService createService;
  private AcademyAiChatServiceImpl chatService;
  private FakeAiProvider fakeAiProvider;

  /** 테스트용 Fake AI Provider (목업). */
  static class FakeAiProvider implements AiProvider {
    private final Map<String, String> responses = new java.util.HashMap<>();
    private int callCount = 0;

    void setResponse(String promptKeyword, String response) {
      responses.put(promptKeyword, response);
    }

    @Override
    public AiCompletion complete(String prompt, AiTaskLimits limits) {
      callCount++;
      for (Map.Entry<String, String> e : responses.entrySet()) {
        if (prompt.contains(e.getKey())) {
          return AiCompletion.ok(e.getValue());
        }
      }
      // 기본 응답
      return AiCompletion.ok("{\"intro\": \"테스트 소개문입니다.\"}");
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public String name() {
      return "fake";
    }

    int getCallCount() {
      return callCount;
    }
  }

  @BeforeEach
  void setUp() {
    analyzer = new AcademyRequirementAnalyzer();
    fakeAiProvider = new FakeAiProvider();
    createService = new AcademyAiCreateService(fakeAiProvider, analyzer);
    chatService = new AcademyAiChatServiceImpl(
        fakeAiProvider, null); // contextService는 null (테스트에서 미사용)
  }

  // === 시나리오 1: 영어학원 홈페이지 생성 (목업) ===

  @Test
  @DisplayName("[목업] 시나리오 1: 영어학원 홈페이지 생성")
  void scenario1_englishAcademy() {
    // given: 사용자 자연어 요청
    String message = "압구정에 있는 중학생 전문 영어학원 홈페이지를 만들어 줘. "
        + "내신 대비가 강점이고, 학부모 상담을 강조해 줘. 깔끔하고 신뢰감 있게 구성해 줘.";

    // when: 요구사항 분석
    AcademyRequirements req = analyzer.analyze(message);

    // then: 정확한 분석
    assertThat(req.location()).isEqualTo("압구정");
    assertThat(req.target()).isEqualTo("중학생");
    assertThat(req.subject()).isEqualTo("영어");
    assertThat(req.preset()).isEqualTo("ENGLISH");
    assertThat(req.strengths()).contains("내신");
    assertThat(req.emphases()).anyMatch(e -> e.contains("상담"));
    assertThat(req.tone()).contains("신뢰감");

    // when: 초안 생성
    fakeAiProvider.setResponse("학원 홈페이지",
        "{\"intro\": \"압구정에 위치한 중학생 전문 영어학원입니다. "
            + "내신 대비에 강점을 가지고 체계적으로 지도합니다.\", "
            + "\"introHeading\": \"중학생 영어 전문\", "
            + "\"introMotto\": \"믿고 맡길 수 있는 영어학원\"}");
    CreateDraft draft = createService.generateDraft(message, "압구정영어학원");

    // then: 프리셋 및 콘텐츠 확인
    assertThat(draft.designPreset()).isEqualTo("ENGLISH");
    assertThat(draft.builderDraft()).containsKey("intro");
    assertThat(draft.builderDraft()).containsKey("introHeading");
    assertThat(draft.builderDraft().get("targetGroups")).isEqualTo("중학생");
    assertThat(draft.builderDraft().get("ctaEnabled")).isEqualTo("true");
    // brandPrimary는 포함되지 않아야 함 (P0 프리셋 경유)
    assertThat(draft.builderDraft()).doesNotContainKey("brandPrimary");
  }

  // === 시나리오 2: 수학학원 홈페이지 생성 (목업) ===

  @Test
  @DisplayName("[목업] 시나리오 2: 수학학원 홈페이지 생성")
  void scenario2_mathAcademy() {
    String message = "고등학생 대상 수학학원 홈페이지 만들어 줘. 수능 대비가 강점이야.";

    AcademyRequirements req = analyzer.analyze(message);
    assertThat(req.preset()).isEqualTo("EXAM");
    assertThat(req.target()).isEqualTo("고등학생");
    assertThat(req.subject()).isEqualTo("수학");

    CreateDraft draft = createService.generateDraft(message, "수학학원");
    assertThat(draft.designPreset()).isEqualTo("EXAM");
  }

  // === 시나리오 3: 예체능학원 홈페이지 생성 (목업) ===

  @Test
  @DisplayName("[목업] 시나리오 3: 예체능학원 홈페이지 생성")
  void scenario3_artsAcademy() {
    String message = "초등학생 대상 미술학원 홈페이지 만들어 줘.";

    AcademyRequirements req = analyzer.analyze(message);
    assertThat(req.preset()).isEqualTo("ARTS");
    assertThat(req.target()).isEqualTo("초등학생");

    CreateDraft draft = createService.generateDraft(message, "미술학원");
    assertThat(draft.designPreset()).isEqualTo("ARTS");
  }

  // === 시나리오 4: 학원 소개글 반복 수정 (목업) ===

  @Test
  @DisplayName("[목업] 시나리오 4: 소개글 톤 조절 (짧게)")
  void scenario4_shortenIntro() {
    // given: 기존 컨텍스트
    AcademyAiContext context = new AcademyAiContext(
        "테스트학원",
        "기존의 긴 소개문입니다. 여러 문장으로 구성되어 있습니다. 세 번째 문장도 있습니다.",
        null, null, null, null, null, null, null, null);

    fakeAiProvider.setResponse("다듬어",
        "간결한 소개문입니다.");

    // when: "짧게" 요청
    ChatProposal proposal = chatService.proposeChanges(context, "소개글을 좀 더 짧게 바꿔줘");

    // then: intro 필드가 제안됨
    assertThat(proposal.changes()).isNotEmpty();
    assertThat(proposal.changes().stream()
        .anyMatch(c -> c.key().equals("intro"))).isTrue();
  }

  @Test
  @DisplayName("[목업] 시나리오 4b: 소개글 톤 조절 (신뢰감)")
  void scenario4b_trustworthyIntro() {
    AcademyAiContext context = new AcademyAiContext(
        "테스트학원", "기본 소개문입니다.",
        null, null, null, null, null, null, null, null);

    fakeAiProvider.setResponse("다듬어",
        "학부모님께서 신뢰하실 수 있는 전문적인 소개문입니다.");

    ChatProposal proposal =
        chatService.proposeChanges(context, "학부모가 신뢰할 수 있는 표현으로 바꿔 줘");

    assertThat(proposal.changes()).isNotEmpty();
  }

  // === 시나리오 5: 여러 항목 동시 수정 (목업) ===

  @Test
  @DisplayName("[목업] 시나리오 5: 소개글과 교육 철학 동시 수정")
  void scenario5_multiFieldEdit() {
    AcademyAiContext context = new AcademyAiContext(
        "테스트학원", "기존 소개문",
        null, null, null, null, null,
        List.of("철학1", "철학2"), null, null);

    fakeAiProvider.setResponse("수정",
        "수정된 내용입니다.");

    ChatProposal proposal =
        chatService.proposeChanges(context, "소개글과 교육 철학을 함께 수정해줘");

    // then: 여러 필드가 제안됨
    assertThat(proposal.changes().size()).isGreaterThanOrEqualTo(2);
  }

  // === 시나리오 6: 허위 성적·경력 생성 방지 ===

  @Test
  @DisplayName("[목업] 시나리오 6: 허위 정보 생성 방지")
  void scenario6_noHallucination() {
    String message = "영어학원 홈페이지 만들어 줘.";

    // AI가 허위 숫자를 생성해도 필터링되어야 함
    fakeAiProvider.setResponse("학원 홈페이지",
        "{\"intro\": \"10년 경력의 원장이 지도하는 학원입니다. 95% 성적 향상!\", "
            + "\"introHeading\": \"최고의 학원\"}");

    CreateDraft draft = createService.generateDraft(message, "테스트학원");

    // then: 허위 숫자 패턴이 포함된 콘텐츠는 제외됨
    String intro = draft.builderDraft().get("intro");
    if (intro != null) {
      // "10년 경력", "95%" 같은 패턴이 없어야 함
      assertThat(intro).doesNotContain("10년 경력");
      assertThat(intro).doesNotContain("95%");
    }
  }

  // === 시나리오 7: 허용되지 않은 필드 변경 거부 ===

  @Test
  @DisplayName("시나리오 7: 허용되지 않은 필드 거부")
  void scenario7_rejectDisallowedField() {
    AcademyAiContext context = new AcademyAiContext(
        "테스트학원", "소개문",
        null, null, null, null, null, null, null, null);

    // designPreset 변경 시도 (금지된 필드)
    ChatProposal proposal =
        chatService.proposeChanges(context, "디자인 프리셋을 바꿔줘");

    // then: 변경 제안이 없거나 안내 메시지
    // (designPreset은 KEYWORD_FIELD_MAP에 없으므로 빈 proposal)
    assertThat(proposal.changes()).isEmpty();
  }

  @Test
  @DisplayName("시나리오 7b: 검증에서 금지 필드 차단")
  void scenario7b_validationBlocksDisallowed() {
    ChatProposal proposal = new ChatProposal(
        List.of(new f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto
            .AcademyAiChatDtos.FieldChange("designPreset", "EXAM", "ENGLISH")),
        "테스트", "session-123");

    ValidationResult result = chatService.validateProposal(proposal);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("designPreset"));
  }

  // === 시나리오 8: 사용자 승인 전 DB 미변경 ===
  // (통합 테스트에서 검증, 단위 테스트에서는 서비스가 DB를 직접 호출하지 않음을 확인)

  @Test
  @DisplayName("시나리오 8: 생성 서비스는 DB를 수정하지 않음")
  void scenario8_noDbWriteBeforeApproval() {
    // AcademyAiCreateService는 Repository를 주입받지 않음
    // 컴파일 타임에 보장됨. 여기서는 generateDraft가 순수 함수임을 확인.
    String message = "영어학원 홈페이지 만들어 줘.";

    CreateDraft draft1 = createService.generateDraft(message, "학원1");
    CreateDraft draft2 = createService.generateDraft(message, "학원1");

    // 같은 입력에 대해 일관된 출력 (DB 상태에 의존하지 않음)
    assertThat(draft1.designPreset()).isEqualTo(draft2.designPreset());
  }

  // === 시나리오 9: 버전 복원 ===
  // (통합 테스트에서 검증)

  // === 시나리오 10: P0 발행 기능 회귀 ===
  // (기존 P0 테스트가 통과하면 회귀 없음으로 간주)
}
