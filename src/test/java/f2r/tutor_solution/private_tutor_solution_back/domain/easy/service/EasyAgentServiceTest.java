package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
class EasyAgentServiceTest {
 EasyTutorAccessService access=mock(EasyTutorAccessService.class);
 EasyWorkspaceService workspace=mock(EasyWorkspaceService.class);
 EasyLedgerInquiryService ledger=mock(EasyLedgerInquiryService.class);
 EasyTutorImportService imports=mock(EasyTutorImportService.class);
 EasyAgentLlmInterpreter llm=mock(EasyAgentLlmInterpreter.class);
 EasyAgentSessionRepository sessions=mock(EasyAgentSessionRepository.class);
 EasyAgentMessageRepository messages=mock(EasyAgentMessageRepository.class);
 EasyAgentActionRepository actions=mock(EasyAgentActionRepository.class);
 EasyImportBatchRepository batches=mock(EasyImportBatchRepository.class);
 EasyImportRowRepository rows=mock(EasyImportRowRepository.class);
 EasyAccountRepository accounts=mock(EasyAccountRepository.class);
 EasyAgentService service;
 JwtTokenProvider.AuthenticatedPrincipal principal=new JwtTokenProvider.AuthenticatedPrincipal(10L,"owner","TUTOR");
 EasyBusiness business;
 @BeforeEach void setUp(){
  service=new EasyAgentService(access,workspace,ledger,imports,llm,sessions,messages,actions,batches,rows,accounts,new ObjectMapper());
  business=EasyBusiness.builder().code("A").name("A").active(true).sortOrder(1).build();
  ReflectionTestUtils.setField(business,"businessId",1L);
 }
 @Test void otherBusinessIsRejectedBeforeActionLookup(){
  when(access.requireAllowedBusiness(principal,2L)).thenThrow(new AccessDeniedException("denied"));
  assertThatThrownBy(()->service.approve(principal,2L,99L)).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(actions,imports);
 }
 @Test void changedProposalBecomesStaleAndNeverApplies(){
  EasyAgentSession session=new EasyAgentSession(business,10L);
  EasyAccount old=account(1L,"소모품비");EasyAccount changed=account(2L,"교재비");
  EasyImportBatch batch=EasyImportBatch.builder().business(business).sourceName("x").sourceKind("CSV").totalRows(1).build();
  ReflectionTestUtils.setField(batch,"batchId",3L);
  EasyImportRow row=EasyImportRow.builder().batch(batch).rowNo(1).amount(100L).build();
  ReflectionTestUtils.setField(row,"rowId",4L);row.markReady(changed,1.0);
  EasyAgentAction action=EasyAgentAction.selectAccount(session,business,10L,3L,4L,2L,old.getAccountId(),"READY","change",LocalDateTime.now().plusMinutes(5));
  when(access.requireAllowedBusiness(principal,1L)).thenReturn(business);
  when(actions.findOwnedForUpdate(7L,1L,10L)).thenReturn(Optional.of(action));
  when(batches.findByBatchIdAndBusiness_BusinessId(3L,1L)).thenReturn(Optional.of(batch));
  when(rows.findByBatch_BatchIdAndRowIdIn(3L,List.of(4L))).thenReturn(List.of(row));
  assertThatThrownBy(()->service.approve(principal,1L,7L)).isInstanceOf(ResponseStatusException.class);
  assertThat(action.getStatus()).isEqualTo(EasyAgentActionStatus.STALE);
  verifyNoInteractions(imports);
 }
 @Test void validApprovalDelegatesToExistingImportService(){
  EasyAgentSession session=new EasyAgentSession(business,10L);
  EasyAccount old=account(1L,"소모품비");
  EasyImportBatch batch=EasyImportBatch.builder().business(business).sourceName("x").sourceKind("CSV").totalRows(1).build();
  ReflectionTestUtils.setField(batch,"batchId",3L);
  EasyImportRow row=EasyImportRow.builder().batch(batch).rowNo(1).amount(100L).build();
  ReflectionTestUtils.setField(row,"rowId",4L);row.markReady(old,.8);
  EasyAgentAction action=EasyAgentAction.selectAccount(session,business,10L,3L,4L,2L,1L,"READY","change",LocalDateTime.now().plusMinutes(5));
  when(access.requireAllowedBusiness(principal,1L)).thenReturn(business);
  when(actions.findOwnedForUpdate(7L,1L,10L)).thenReturn(Optional.of(action));
  when(batches.findByBatchIdAndBusiness_BusinessId(3L,1L)).thenReturn(Optional.of(batch));
  when(rows.findByBatch_BatchIdAndRowIdIn(3L,List.of(4L))).thenReturn(List.of(row));
  service.approve(principal,1L,7L);
  verify(imports).selectAccount(principal,1L,3L,4L,2L);
  assertThat(action.getStatus()).isEqualTo(EasyAgentActionStatus.APPLIED);
 }
 private EasyAccount account(Long id,String name){
  EasyAccount a=EasyAccount.builder().code("C"+id).name(name).direction(EasyAccountDirection.EXPENSE).sortOrder(id.intValue()).active(true).build();
  ReflectionTestUtils.setField(a,"accountId",id);return a;
 }
}
