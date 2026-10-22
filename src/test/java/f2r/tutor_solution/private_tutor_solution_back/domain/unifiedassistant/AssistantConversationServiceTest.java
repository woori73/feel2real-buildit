package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
class AssistantConversationServiceTest {
  final UnifiedAssistantService service=mock(UnifiedAssistantService.class);
  final AssistantCommandParser parser=new AssistantCommandParser();
  final AssistantIntentAnalyzer intents=new AssistantIntentAnalyzer(parser,mock(f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.MetaMuseSparkLlmTransport.class));
  final AssistantConversationService chat=new AssistantConversationService(service,intents,new AssistantMaterialAnalyzer(intents));
  final AuthenticatedPrincipal p=new AuthenticatedPrincipal(1L,"owner","TUTOR");
  final AssistantDraft draft=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{}","{}","snapshot");
  @Test void givenCompleteInstruction_whenSent_thenCreatesDraftWithoutExecuting() {
    when(service.draft(eq(p),eq(7L),eq(1L),eq("STUDENT_CREATE"),any())).thenReturn(draft);
    var r=chat.send(p,7L,1L,"학생 하늘 등록, 학년: 중2");
    assertThat(r.drafts()).containsExactly(draft);verify(service,never()).decide(any(),any(),any(),anyBoolean());
  }
  @Test void givenMissingStudentId_whenSent_thenAsksForCompletionWithoutDraft() {
    var r=chat.send(p,7L,1L,"학생 결석 기록");assertThat(r.drafts()).isEmpty();assertThat(r.message()).contains("빠진 값");
    verify(service,never()).draft(any(),any(),any(),any(),any());
  }
  @Test void givenInvalidRosterRows_whenUploaded_thenReportsPartialDraftsAndIssues() throws Exception {
    when(service.draft(eq(p),eq(7L),eq(1L),eq("STUDENT_CREATE"),any())).thenReturn(draft);
    var r=chat.upload(p,7L,1L,"students.csv","이름,학교\n하늘,새봄중\n,학교".getBytes(StandardCharsets.UTF_8));
    assertThat(r.drafts()).hasSize(1);assertThat(r.issues()).hasSize(1);assertThat(r.message()).contains("아직 실제 업무에 저장하지");
  }
  @Test void givenMixedMaterialsWithDeniedScope_whenUploaded_thenNoPartialDraftsAreCreated() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(service).checkDraftScope(p,7L,1L,"LEDGER_ENTRY");
    assertThatThrownBy(()->chat.upload(p,7L,1L,"work.txt","학생 하늘 등록\n2026-10-22 1000원 지출 등록".getBytes(StandardCharsets.UTF_8))).isInstanceOf(ResponseStatusException.class);
    verify(service,never()).draft(any(),any(),any(),any(),any());
  }
}
