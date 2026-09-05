package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.Composition.SectionState;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiProposal.Type;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiTaskType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AcademyAiProposalService — 전체 진단 JSON 파싱 + proposal별 allowlist 검증.
 *
 * <p>절대 원칙: 이 서비스는 조회 + provider 호출 + 검증만 한다 — Academy/builderDraft를 절대
 * 수정하지 않는다(수정 API 자체가 없어 구조적으로 불가능). malformed 응답/ provider 장애 시에도
 * 예외만 던지고 되돌릴 상태가 없다.
 */
@DisplayName("AcademyAiProposalService — 진단 JSON 파싱 + allowlist 검증")
class AcademyAiProposalServiceTest {

  /** 서버가 인정하는 15개 관리 섹션 순서(AcademyService.DEFAULT_SECTION_ORDER와 동일). */
  private static final List<String> CANONICAL_ORDER = List.of(
      "sec-hero", "sec-featured", "sec-intro", "sec-principal", "sec-philosophy",
      "sec-videos", "sec-minilectures", "sec-reviews", "sec-lessons", "sec-curriculum",
      "sec-effect", "sec-digitaltwin", "sec-market", "sec-payment", "sec-social");

  private AcademyAiContextService contextService;
  private AcademyAiPromptBuilder promptBuilder;
  private FakeAiProvider provider;
  private AcademyAiProposalService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    contextService = mock(AcademyAiContextService.class);
    promptBuilder = mock(AcademyAiPromptBuilder.class);
    provider = new FakeAiProvider();
    service = new AcademyAiProposalService(contextService, promptBuilder, provider);
    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
    when(contextService.buildContext(principal)).thenReturn(context());
    when(promptBuilder.build(any(), any(), any(), any(), any())).thenReturn("PROMPT");
  }

  private static AcademyAiContext context() {
    List<SectionState> sections = new ArrayList<>();
    for (String key : CANONICAL_ORDER) {
      boolean editable = !List.of("sec-reviews", "sec-curriculum", "sec-effect", "sec-market", "sec-payment")
          .contains(key);
      sections.add(new SectionState(key, key, true, editable, false));
    }
    Composition composition = new Composition(
        sections, false, 1, 2, 3, 1, "상담 신청하기",
        List.of("principalDesc", "philosophyItem1", "featuredCtaLabel"), 40, List.of("원장 소개"));
    return new AcademyAiContext(
        "우리학원", "소개", List.of("영어"), List.of("MIDDLE"),
        "김원장", "대표원장", "기존 소개문", List.of("철학1", "", "", ""), List.of(), composition);
  }

  private static String reordered(String moveKey, int toIndex) {
    List<String> order = new ArrayList<>(CANONICAL_ORDER);
    order.remove(moveKey);
    order.add(toIndex, moveKey);
    return "[\"" + String.join("\",\"", order) + "\"]";
  }

  @Test
  @DisplayName("given 정상 JSON when 생성 then diagnosis + 검증 통과한 proposal을 돌려준다")
  void generate_returns_parsed_proposal() {
    provider.respondWith("""
        {
          "diagnosis": "수업 콘텐츠가 뒤쪽에 있어 강점이 늦게 드러납니다.",
          "proposals": [
            {"id":"p1","type":"TEXT","title":"원장 소개","current":"비어 있음",
             "suggestion":"신뢰감 있게 작성","target":"principalDesc",
             "suggestedValue":"20년간 지역 학생들과 함께한 원장이 직접 지도합니다.","safeToApply":true}
          ]
        }
        """);

    AcademyAiProposal proposal = service.generate(principal, "학부모가 신뢰할 수 있게");

    assertThat(proposal.diagnosis()).contains("강점이 늦게");
    assertThat(proposal.proposals()).hasSize(1);
    AcademyAiProposal.Item item = proposal.proposals().get(0);
    assertThat(item.type()).isEqualTo(Type.TEXT);
    assertThat(item.target()).isEqualTo("principalDesc");
    assertThat(item.textValue()).contains("20년간");
    assertThat(item.safeToApply()).isTrue();
    assertThat(provider.lastPrompt).isEqualTo("PROMPT");
  }

  @Test
  @DisplayName("given 사용자 요구사항 when 생성 then promptBuilder에 그대로 전달된다")
  void generate_passes_instructions_to_prompt_builder() {
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[]}");
    service.generate(principal, "수업 영상을 중심으로 보여줘");

    org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(promptBuilder)
        .build(org.mockito.ArgumentMatchers.eq(AcademyAiTaskType.ACADEMY_COMPOSE), any(), any(), any(), captor.capture());
    assertThat(captor.getValue()).isEqualTo("수업 영상을 중심으로 보여줘");
  }

  @Test
  @DisplayName("given malformed JSON 응답 when 생성 then AcademyAiGenerationException(Academy 안 건드림)")
  void generate_rejects_malformed_json() {
    provider.respondWith("여기 개선안입니다: 원장 소개를 고치세요. (JSON 아님)");
    assertThatThrownBy(() -> service.generate(principal, ""))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given diagnosis가 빈 JSON when 생성 then 거부한다")
  void generate_rejects_blank_diagnosis() {
    provider.respondWith("{\"diagnosis\":\"  \",\"proposals\":[]}");
    assertThatThrownBy(() -> service.generate(principal, ""))
        .isInstanceOf(AcademyAiGenerationException.class);
  }

  @Test
  @DisplayName("given allowlist 밖 target(monthlyTuition 등) proposal when 생성 then 그 항목만 drop")
  void generate_drops_proposal_with_disallowed_target() {
    provider.respondWith("""
        {"diagnosis":"진단","proposals":[
          {"id":"p1","type":"TEXT","title":"수강료","current":"x","suggestion":"내려라",
           "target":"monthlyTuition","suggestedValue":"20만원","safeToApply":true},
          {"id":"p2","type":"TEXT","title":"원장 소개","current":"x","suggestion":"다듬어라",
           "target":"principalDesc","suggestedValue":"신뢰감 있는 소개문입니다.","safeToApply":true}
        ]}
        """);

    AcademyAiProposal proposal = service.generate(principal, "");
    assertThat(proposal.proposals()).hasSize(1);
    assertThat(proposal.proposals().get(0).target()).isEqualTo("principalDesc");
  }

  @Test
  @DisplayName("given 알 수 없는 type proposal when 생성 then drop한다")
  void generate_drops_unknown_type() {
    provider.respondWith("""
        {"diagnosis":"진단","proposals":[
          {"id":"p1","type":"DELETE_SECTION","title":"삭제","current":"x","suggestion":"지워라",
           "target":"sec-videos","suggestedValue":"x","safeToApply":true}
        ]}
        """);
    assertThat(service.generate(principal, "").proposals()).isEmpty();
  }

  @Test
  @DisplayName("given 지나치게 긴 TEXT suggestedValue when 생성 then 그 proposal을 통째로 거부한다(자르지 않음)")
  void generate_drops_oversized_text_value() {
    String huge = "가".repeat(300);
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[{\"id\":\"p1\",\"type\":\"TEXT\","
        + "\"title\":\"철학\",\"current\":\"x\",\"suggestion\":\"y\",\"target\":\"philosophyItem1\","
        + "\"suggestedValue\":\"" + huge + "\",\"safeToApply\":true}]}");
    assertThat(service.generate(principal, "").proposals()).isEmpty();
  }

  @Test
  @DisplayName("given 정상 SECTION_ORDER(15개 재배열, sec-hero 0번) when 생성 then 통과한다")
  void generate_accepts_valid_section_order() {
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[{\"id\":\"p1\",\"type\":\"SECTION_ORDER\","
        + "\"title\":\"섹션 순서\",\"current\":\"수업이 뒤쪽\",\"suggestion\":\"수업 영상을 앞으로\","
        + "\"target\":null,\"suggestedValue\":" + reordered("sec-videos", 1) + ",\"safeToApply\":true}]}");

    AcademyAiProposal proposal = service.generate(principal, "");
    assertThat(proposal.proposals()).hasSize(1);
    assertThat(proposal.proposals().get(0).type()).isEqualTo(Type.SECTION_ORDER);
    assertThat(proposal.proposals().get(0).sectionOrder()).hasSize(15).startsWith("sec-hero");
  }

  @Test
  @DisplayName("given SECTION_ORDER의 크기가 다르거나 sec-hero가 0번이 아니면 when 생성 then drop한다")
  void generate_drops_invalid_section_order() {
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[{\"id\":\"p1\",\"type\":\"SECTION_ORDER\","
        + "\"title\":\"섹션 순서\",\"current\":\"x\",\"suggestion\":\"y\",\"target\":null,"
        + "\"suggestedValue\":[\"sec-intro\",\"sec-hero\"],\"safeToApply\":true}]}");
    assertThat(service.generate(principal, "").proposals()).isEmpty();
  }

  @Test
  @DisplayName("given SECTION_ORDER가 [AI 변경 금지] 섹션(sec-reviews)의 위치를 바꾸면 when 생성 then drop한다")
  void generate_drops_section_order_that_moves_locked_section() {
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[{\"id\":\"p1\",\"type\":\"SECTION_ORDER\","
        + "\"title\":\"섹션 순서\",\"current\":\"x\",\"suggestion\":\"y\",\"target\":null,"
        + "\"suggestedValue\":" + reordered("sec-reviews", 1) + ",\"safeToApply\":true}]}");
    assertThat(service.generate(principal, "").proposals()).isEmpty();
  }

  @Test
  @DisplayName("given SECTION_ORDER가 현재 순서와 동일하면 when 생성 then 의미 없으므로 drop한다")
  void generate_drops_noop_section_order() {
    provider.respondWith("{\"diagnosis\":\"진단\",\"proposals\":[{\"id\":\"p1\",\"type\":\"SECTION_ORDER\","
        + "\"title\":\"섹션 순서\",\"current\":\"x\",\"suggestion\":\"y\",\"target\":null,"
        + "\"suggestedValue\":[\"" + String.join("\",\"", CANONICAL_ORDER) + "\"],\"safeToApply\":true}]}");
    assertThat(service.generate(principal, "").proposals()).isEmpty();
  }

  @Test
  @DisplayName("given provider 장애 when 생성 then AcademyAiGenerationException + 기존 Academy 보존")
  void generate_surfaces_provider_failure() {
    provider.failWith("AI 서비스 연결에 실패했습니다.");
    assertThatThrownBy(() -> service.generate(principal, ""))
        .isInstanceOf(AcademyAiGenerationException.class)
        .hasMessageContaining("연결에 실패");
  }

  @Test
  @DisplayName("given 요구사항이 지나치게 김 when 생성 then provider 호출 전에 거부한다")
  void generate_rejects_oversized_instructions() {
    assertThatThrownBy(() -> service.generate(principal, "가".repeat(500)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(provider.callCount).isZero();
    verifyNoInteractions(contextService);
  }

  @Test
  @DisplayName("given proposals가 6개를 초과 when 생성 then 최대 6개까지만 담는다")
  void generate_caps_proposals_at_six() {
    StringBuilder sb = new StringBuilder("{\"diagnosis\":\"진단\",\"proposals\":[");
    for (int i = 0; i < 10; i++) {
      if (i > 0) sb.append(",");
      sb.append("{\"id\":\"p").append(i).append("\",\"type\":\"TEXT\",\"title\":\"t\",\"current\":\"c\",")
          .append("\"suggestion\":\"s\",\"target\":\"philosophyItem1\",\"suggestedValue\":\"짧은 문구 ")
          .append(i).append("\",\"safeToApply\":true}");
    }
    sb.append("]}");
    provider.respondWith(sb.toString());

    assertThat(service.generate(principal, "").proposals()).hasSize(6);
  }

  @Test
  @DisplayName("given JSON이 코드펜스로 감싸져 있음 when 생성 then 방어적으로 파싱한다")
  void generate_strips_code_fence() {
    provider.respondWith("```json\n{\"diagnosis\":\"진단\",\"proposals\":[]}\n```");
    assertThat(service.generate(principal, "").diagnosis()).isEqualTo("진단");
  }
}
