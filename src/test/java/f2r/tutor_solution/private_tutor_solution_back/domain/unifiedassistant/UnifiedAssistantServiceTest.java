package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
@ExtendWith(MockitoExtension.class)
class UnifiedAssistantServiceTest {
  @Mock UserRepository users; @Mock AssistantProgressRepository progress; @Mock AssistantDraftRepository drafts;
  @Mock AssistantAuditRepository audit; @Mock AssistantDraftTransactions transactions; @Mock AcademyAssistantAdapter academy;
  @Mock EasyTutorAccessService access; @Mock EasyTutorImportService imports; @Mock EasyWorkspaceService workspace;
  @InjectMocks UnifiedAssistantService service;
  final AuthenticatedPrincipal p=new AuthenticatedPrincipal(1L,"owner","TUTOR");
  @BeforeEach void actor() {
    when(users.findById(1L)).thenReturn(Optional.of(User.builder().loginId("owner").role(Role.TUTOR).build()));
    ReflectionTestUtils.setField(service,"writeEnabled",true);
  }
  @Test void givenRevokedRole_whenLoadingContext_thenDenied() {
    when(users.findById(1L)).thenReturn(Optional.of(User.builder().role(Role.STUDENT).build()));
    assertThatThrownBy(()->service.overview(p,null)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(academy,workspace);
  }
  @Test void givenDisabledWrites_whenCreatingDraft_thenNoDomainCalls() {
    ReflectionTestUtils.setField(service,"writeEnabled",false);
    assertThatThrownBy(()->service.draft(p,7L,null,"STUDENT_CREATE",AssistantJson.read("{\"name\":\"하늘\"}"))).hasMessageContaining("비활성화");
    verifyNoInteractions(academy,drafts);
  }
  @Test void givenFractionalMoney_whenDrafted_thenRejectedWithoutTruncating() {
    assertThatThrownBy(()->service.draft(p,null,1L,"LEDGER_ENTRY",AssistantJson.read("{\"occurredOn\":\"2099-10-15\",\"direction\":\"EXPENSE\",\"amount\":1.5}"))).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(imports);
  }
  @Test void givenSmuggledApprovalOrOwnerField_whenDrafting_thenRejected() {
    assertThatThrownBy(()->service.draft(p,7L,null,"STUDENT_CREATE",AssistantJson.read("{\"name\":\"하늘\",\"ownerId\":2}"))).hasMessageContaining("허용되지 않은");
    assertThatThrownBy(()->service.draft(p,7L,null,"STUDENT_CREATE",AssistantJson.read("{\"name\":\"하늘\",\"approved\":true}"))).hasMessageContaining("허용되지 않은");
    verifyNoInteractions(academy,drafts);
  }
  @Test void givenBatchNotCreatedByAssistant_whenDraftingAccountChange_thenForbiddenWithoutReadingBatch() {
    // given: 금융자료 수집 등 다른 경로가 만든 승인 전 배치
    when(drafts.existsByOwnerIdAndBusinessIdAndOperationAndPayload(1L,3L,"LEDGER_IMPORT","{\"batchId\":99}")).thenReturn(false);
    // when / then
    assertThatThrownBy(()->service.draft(p,null,3L,"LEDGER_ACCOUNT",AssistantJson.read("{\"batchId\":99,\"rowId\":1,\"accountId\":2}")))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("이 비서가 만든");
    verify(imports,never()).detail(any(),any(),any());
    verify(imports,never()).selectAccount(any(),any(),any(),any(),any());
  }
  @Test void givenBatchFromOwnAssistantImport_whenDraftingAccountChange_thenGuardPassesAndBatchIsRead() {
    // given
    when(drafts.existsByOwnerIdAndBusinessIdAndOperationAndPayload(1L,3L,"LEDGER_IMPORT","{\"batchId\":99}")).thenReturn(true);
    when(imports.detail(any(),eq(3L),eq(99L))).thenThrow(new IllegalStateException("sentinel"));
    // when / then: 가드를 지나 실제 배치 조회까지 도달
    assertThatThrownBy(()->service.draft(p,null,3L,"LEDGER_ACCOUNT",AssistantJson.read("{\"batchId\":99,\"rowId\":1,\"accountId\":2}")))
        .isInstanceOf(IllegalStateException.class).hasMessage("sentinel");
  }
  private AssistantDraft givenClaimedDraft() {
    var snapshot=AssistantJson.read("{\"context\":{\"memberId\":10}}");
    var d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{}",AssistantJson.hash(AssistantJson.write(snapshot)));
    when(drafts.findById(d.getId())).thenReturn(Optional.of(d));
    when(transactions.claim(1L,"TUTOR",d.getId(),d.getPlanHash(),false)).thenAnswer(i->{d.finish("EXECUTING",null);return d;});
    when(transactions.finish(eq(1L),eq(d.getId()),anyString(),anyString())).thenAnswer(i->{d.finish(i.getArgument(2),i.getArgument(3));return d;});
    return d;
  }
  @Test void givenChangedTarget_whenApproved_thenInvalidatedWithoutWrite() {
    var d=givenClaimedDraft();when(academy.snapshot(eq(p),eq(7L),eq("STUDENT_CREATE"),any())).thenReturn(AssistantJson.read("{\"context\":{\"memberId\":11}}"));
    assertThat(service.decide(p,d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("INVALIDATED");verify(academy,never()).execute(any(),any());
  }
  @Test void givenUpstreamTimeout_whenApproved_thenUnknownAndNoAutomaticRetry() {
    var d=givenClaimedDraft();when(academy.snapshot(eq(p),eq(7L),eq("STUDENT_CREATE"),any())).thenReturn(AssistantJson.read("{\"context\":{\"memberId\":10}}"));
    when(academy.execute(p,d)).thenThrow(new RuntimeException("timeout"));
    assertThat(service.decide(p,d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("UNKNOWN");verify(academy,times(1)).execute(p,d);
  }
  @Test void givenDefiniteWriteRejection_whenApproved_thenFailedAndNoAutomaticRetry() {
    var d=givenClaimedDraft();when(academy.snapshot(eq(p),eq(7L),eq("STUDENT_CREATE"),any())).thenReturn(AssistantJson.read("{\"context\":{\"memberId\":10}}"));
    when(academy.execute(p,d)).thenThrow(new AcademyAssistantAdapter.WriteRejected(org.springframework.http.HttpStatus.CONFLICT));
    assertThat(service.decide(p,d.getId(),d.getPlanHash(),false).getStatus()).isEqualTo("FAILED");verify(academy,times(1)).execute(p,d);
  }
  @Test void givenAttendanceEvents_whenQueried_thenOnlyMinimumFieldsAndLimit() {
    when(academy.context(p,7L)).thenReturn(AssistantJson.read("{\"academyId\":7}"));
    when(academy.base(7L)).thenReturn("/api/rea/7");
    var events=new StringBuilder("[");for(int i=1;i<=25;i++)events.append(i>1?",":"").append("{\"id\":"+i+",\"eventType\":\"ABSENT\",\"occurredAt\":\"2026-10-22T10:00\",\"note\":\"비공개 메모\",\"parentPhone\":\"010\"}");
    when(academy.request(p,"GET","/api/rea/7/attendance/student/5",null)).thenReturn(AssistantJson.read(events+"]"));
    var result=service.attendanceQuery(p,7L,5L);
    assertThat((java.util.List<?>)result.get("events")).hasSize(20);
    assertThat(result.toString()).doesNotContain("비공개 메모").doesNotContain("parentPhone");
  }
  @Test void givenRevokedRole_whenQueryingAttendance_thenNoAcademyCall() {
    when(users.findById(1L)).thenReturn(Optional.of(User.builder().role(Role.STUDENT).build()));
    assertThatThrownBy(()->service.attendanceQuery(p,7L,5L)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(academy);
  }
  @Test void givenHistoryRequest_whenListing_thenRecoversStaleExecutingFirst() {
    service.history(p);verify(transactions).recoverStale(1L);
  }
}
