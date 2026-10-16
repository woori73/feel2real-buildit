package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity.PublishingJob;
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
}
