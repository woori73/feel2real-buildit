package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishingAiServiceTest {
  @Test
  void plan_readsDocumentContext_andNeverAppliesBeforeApproval() {
    // given
    var transport = mock(MetaMuseSparkLlmTransport.class);
    var jobs = mock(PublishingJobService.class);
    var edits = mock(PublishingEditService.class);
    var principal = mock(AuthenticatedPrincipal.class);
    var job = new PublishingJob(1L, "교재", PublishingJob.Template.KO_TEXTBOOK_A4);
    when(jobs.requireOwned(principal, 7L)).thenReturn(job);
    when(edits.documentContext(job)).thenReturn("{\"stories\":[{\"story\":20,\"text\":\"원문입니다.\"}]}");
    when(transport.complete(anyString(), anyString(), anyInt())).thenReturn(
        NewStartLlmCompletion.success("{\"plan\":[{\"command\":\"document.replaceText\",\"params\":{\"story\":20,\"before\":\"원문입니다.\",\"after\":\"수정했습니다.\"},\"description\":\"문장 수정\"}]}", "test", "test"));
    var service = new PublishingAiService(transport, jobs, edits, new CommandAllowlist(), new ObjectMapper());
    // when
    var plan = service.plan(principal, 7L, "문장을 다듬어줘");
    // then
    assertThat(plan.needsApproval()).isTrue();
    assertThat(plan.blocked()).isFalse();
    verify(edits, never()).executeCommands(any(), any(), anyList());
    var prompt = ArgumentCaptor.forClass(String.class);
    verify(transport).complete(contains("명령 계획자"), prompt.capture(), eq(12000));
    assertThat(prompt.getValue()).contains("원문입니다.");
    // when explicit approval invokes apply
    when(edits.executeCommands(eq(principal), eq(7L), anyList())).thenReturn(9L);
    assertThat(service.applyPlan(principal, 7L, plan.plan())).isEqualTo(9L);
    verify(edits).executeCommands(eq(principal), eq(7L), anyList());
  }

  @Test
  void plan_warnsWhenAiTargetsLockedPassageStory() {
    // given: 지문 스토리(20)가 잠긴 학습지 작업
    var transport = mock(MetaMuseSparkLlmTransport.class);
    var jobs = mock(PublishingJobService.class);
    var edits = mock(PublishingEditService.class);
    var principal = mock(AuthenticatedPrincipal.class);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    job.setLockedStories("[20]");
    when(jobs.requireOwned(principal, 7L)).thenReturn(job);
    when(edits.documentContext(job)).thenReturn("{\"stories\":[{\"story\":20,\"text\":\"Passage.\"}]}");
    when(transport.complete(anyString(), anyString(), anyInt())).thenReturn(
        NewStartLlmCompletion.success("{\"plan\":[{\"command\":\"document.replaceText\",\"params\":{\"story\":20,\"before\":\"Passage.\",\"after\":\"Changed.\"},\"description\":\"지문 수정\"}]}", "test", "test"));
    var service = new PublishingAiService(transport, jobs, edits, new CommandAllowlist(), new ObjectMapper());
    // when
    var plan = service.plan(principal, 7L, "지문을 다듬어줘");
    // then: 차단이 아니라 경고와 함께 승인 (사용자 명시 승인 시에만 적용 가능)
    assertThat(plan.blocked()).isFalse();
    assertThat(plan.warning()).contains("원문 보호");
    assertThat(plan.needsApproval()).isTrue();
    verify(edits, never()).executeCommands(any(), any(), anyList());
  }

  @Test
  void applyPlan_stripsUserConfirmedFromAiCommands() {
    // given: AI 계획에 userConfirmed이 포함되어도
    var transport = mock(MetaMuseSparkLlmTransport.class);
    var jobs = mock(PublishingJobService.class);
    var edits = mock(PublishingEditService.class);
    var principal = mock(AuthenticatedPrincipal.class);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    job.setLockedStories("[20]");
    when(jobs.requireOwned(principal, 7L)).thenReturn(job);
    var service = new PublishingAiService(transport, jobs, edits, new CommandAllowlist(), new ObjectMapper());
    var planned = List.of(new PublishingAiService.PlannedCommand("document.replaceText",
        new java.util.HashMap<>(Map.of("story", 20, "before", "a", "after", "b", "userConfirmed", true)), "desc"));
    var captor = ArgumentCaptor.forClass(List.class);
    when(edits.executeCommands(eq(principal), eq(7L), captor.capture())).thenReturn(9L);
    // when: 사용자 확인 없이 적용
    service.applyPlan(principal, 7L, planned, false);
    // then: 실행 명령에서 userConfirmed이 제거됨
    List<?> sent = captor.getValue();
    assertThat(sent).hasSize(1);
    var cmd = (f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand) sent.get(0);
    assertThat(cmd.params()).doesNotContainKey("userConfirmed");
  }

  @Test
  void applyPlan_setsUserConfirmedWhenUserExplicitlyConfirms() {
    // given: 사용자가 명시적으로 확인한 경우
    var transport = mock(MetaMuseSparkLlmTransport.class);
    var jobs = mock(PublishingJobService.class);
    var edits = mock(PublishingEditService.class);
    var principal = mock(AuthenticatedPrincipal.class);
    var job = new PublishingJob(1L, "학습지", PublishingJob.Template.EN_WORKSHEET_A4);
    job.setLockedStories("[20]");
    when(jobs.requireOwned(principal, 7L)).thenReturn(job);
    var service = new PublishingAiService(transport, jobs, edits, new CommandAllowlist(), new ObjectMapper());
    var planned = List.of(new PublishingAiService.PlannedCommand("document.replaceText",
        Map.of("story", 20, "before", "a", "after", "b"), "desc"));
    var captor = ArgumentCaptor.forClass(List.class);
    when(edits.executeCommands(eq(principal), eq(7L), captor.capture())).thenReturn(9L);
    // when
    service.applyPlan(principal, 7L, planned, true);
    // then: 잠금 스토리 대상 명령에 userConfirmed=true 설정
    List<?> sent = captor.getValue();
    var cmd = (f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto.PublishingCommand) sent.get(0);
    assertThat(cmd.params()).containsEntry("userConfirmed", true);
  }
}
