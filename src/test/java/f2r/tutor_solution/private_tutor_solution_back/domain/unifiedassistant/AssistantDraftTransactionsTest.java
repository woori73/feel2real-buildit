package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
class AssistantDraftTransactionsTest {
  AssistantDraftRepository repo=mock(AssistantDraftRepository.class);
  AssistantAuditRepository audit=mock(AssistantAuditRepository.class);
  AssistantDraftTransactions tx=new AssistantDraftTransactions(repo,audit);
  AssistantDraft d;
  @BeforeEach void givenOwnedPendingDraft() {
    d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{}","snapshot");
    when(repo.ownedForUpdate(d.getId(),1L)).thenReturn(Optional.of(d));
    lenient().when(repo.claimPending(anyString(),anyLong(),anyString(),any())).thenReturn(1);
  }
  @Test void givenValidHash_whenApproved_thenClaimsAndAuditsBeforeExecution() {
    assertThat(tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("EXECUTING");
    verify(audit).save(any());
    assertThatThrownBy(()->tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
  }
  @Test void givenStaleManagedPendingDraft_whenDatabaseClaimLost_thenNoExecutionOrAudit() {
    when(repo.claimPending(anyString(),anyLong(),anyString(),any())).thenReturn(0);
    assertThatThrownBy(()->tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(audit);
  }
  @Test void givenOtherOwnerOrRole_whenApproved_thenDenied() {
    when(repo.ownedForUpdate(d.getId(),2L)).thenReturn(Optional.empty());
    assertThatThrownBy(()->tx.claim(2L,"TUTOR",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(()->tx.claim(1L,"ADMIN",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
    assertThat(d.getStatus()).isEqualTo("PENDING");
  }
  @Test void givenWrongHashOrTamperedInput_whenApproved_thenDeniedWithoutExecution() {
    assertThatThrownBy(()->tx.claim(1L,"TUTOR",d.getId(),"forged",false)).isInstanceOf(ResponseStatusException.class);
    ReflectionTestUtils.setField(d,"payload","{\"name\":\"tampered\"}");
    assertThatThrownBy(()->tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
    assertThat(d.getStatus()).isEqualTo("PENDING");
  }
  @Test void givenExpiredDraft_whenApproved_thenExpiresWithoutExecution() {
    ReflectionTestUtils.setField(d,"expiresAt",LocalDateTime.now().minusSeconds(1));
    assertThat(tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("EXPIRED");
  }
  @Test void givenCancelledDraft_whenApproved_thenCannotRevive() {
    tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),true);
    assertThatThrownBy(()->tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false)).isInstanceOf(ResponseStatusException.class);
  }
  @Test void givenVerifiedDraft_whenApprovedAgain_thenReturnsSavedResult() {
    tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false);tx.finish(1L,d.getId(),"VERIFIED","{}");
    assertThat(tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("VERIFIED");
  }
  @Test void givenExecutingPastGrace_whenRecovered_thenUnknownWithScopedAudit() {
    d.finish("EXECUTING",null);
    when(repo.findByOwnerIdAndStatusAndExpiresAtBefore(eq(1L),eq("EXECUTING"),any())).thenReturn(java.util.List.of(d));
    assertThat(tx.recoverStale(1L)).isEqualTo(1);
    assertThat(d.getStatus()).isEqualTo("UNKNOWN");assertThat(d.getResult()).contains("자동 재실행하지 않습니다");
    var captor=org.mockito.ArgumentCaptor.forClass(AssistantAudit.class);verify(audit).save(captor.capture());
    assertThat(captor.getValue().getEvent()).isEqualTo("RECOVERED_UNKNOWN");assertThat(captor.getValue().getOperation()).isEqualTo("STUDENT_CREATE");assertThat(captor.getValue().getAcademyId()).isEqualTo(7L);
  }
  @Test void givenApproval_whenClaimed_thenAuditCarriesOperationAndScope() {
    tx.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false);
    var captor=org.mockito.ArgumentCaptor.forClass(AssistantAudit.class);verify(audit).save(captor.capture());
    assertThat(captor.getValue().getOperation()).isEqualTo("STUDENT_CREATE");assertThat(captor.getValue().getAcademyId()).isEqualTo(7L);assertThat(captor.getValue().getBusinessId()).isNull();
  }
}
