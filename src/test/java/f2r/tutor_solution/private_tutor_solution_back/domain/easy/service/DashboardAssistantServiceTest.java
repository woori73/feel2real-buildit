package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

@DisplayName("대시보드 AI 비서(읽기 전용, Muse Spark)")
class DashboardAssistantServiceTest {

  EasyTutorAccessService access = mock(EasyTutorAccessService.class);
  EasyWorkspaceService workspace = mock(EasyWorkspaceService.class);
  MetaMuseSparkLlmTransport transport = mock(MetaMuseSparkLlmTransport.class);
  DashboardAssistantService service = new DashboardAssistantService(access, workspace, transport);
  JwtTokenProvider.AuthenticatedPrincipal principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
  EasyBusiness business = mock(EasyBusiness.class);

  @BeforeEach
  void setUp() {
    when(business.getBusinessId()).thenReturn(7L);
    when(access.requireAllowedBusiness(principal, 7L)).thenReturn(business);
    when(workspace.monthly(eq(7L), any())).thenReturn(new EasyWorkspaceMonthlyResponse(
        "2026-10", 7L, "테스트학원", 1_000_000, 300_000, 700_000, 50_000, 12, 0, 0, 0, 0, 2, 0,
        "OPEN", false, EasyWorkspaceMonthlyResponse.Sources.contract(), List.of(), List.of()));
  }

  @Test
  @DisplayName("given 허용된 사업체 when 질문하면 then 월 요약 문맥과 함께 Muse를 호출하고 답을 돌려준다")
  void asksMuseWithServerContext() {
    // given
    when(transport.complete(anyString(), contains("수입 1000000원"), anyInt())).thenReturn(
        NewStartLlmCompletion.success("이번 달 수지는 70만원입니다.", "meta-muse-spark", "muse-spark-1.3-contributor"));
    // when
    var answer = service.ask(principal, 7L, YearMonth.of(2026, 10), "이번 달 수지는?");
    // then
    assertThat(answer.ok()).isTrue();
    assertThat(answer.text()).contains("70만원");
    assertThat(answer.model()).isEqualTo("muse-spark-1.3-contributor");
  }

  @Test
  @DisplayName("given Muse 호출 실패 when 질문하면 then 다른 모델로 대체하지 않고 오류 안내를 돌려준다")
  void failureIsReportedNotSubstituted() {
    // given
    when(transport.complete(anyString(), anyString(), anyInt()))
        .thenReturn(NewStartLlmCompletion.failed("AI 서비스 연결에 실패했습니다.", true));
    // when
    var answer = service.ask(principal, 7L, null, "미납 원생은?");
    // then
    assertThat(answer.ok()).isFalse();
    assertThat(answer.text()).contains("연결에 실패");
  }

  @Test
  @DisplayName("given 접근 권한 없는 사업체 when 질문하면 then 예외로 막고 Muse를 호출하지 않는다")
  void deniedBusinessNeverReachesModel() {
    // given
    when(access.requireAllowedBusiness(principal, 99L)).thenThrow(new AccessDeniedException("no"));
    // when / then
    assertThatThrownBy(() -> service.ask(principal, 99L, null, "수입은?")).isInstanceOf(AccessDeniedException.class);
    verify(transport, never()).complete(anyString(), anyString(), anyInt());
  }

  @Test
  @DisplayName("given 빈 질문 또는 과도하게 긴 질문 when 질문하면 then 거부한다")
  void rejectsBlankOrTooLong() {
    assertThatThrownBy(() -> service.ask(principal, 7L, null, "  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.ask(principal, 7L, null, "가".repeat(1001)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
