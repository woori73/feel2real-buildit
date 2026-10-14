package f2r.tutor_solution.private_tutor_solution_back.domain.easy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
@SpringBootTest @Transactional
class EasyAgentFlowMySqlTest {
 @Autowired EasyAgentService agent;
 @Autowired EasyTutorImportService imports;
 @Autowired EasyBusinessRepository businesses;
 @Autowired EasyAccountRepository accounts;
 @Autowired EasyImportRowRepository rows;
 @Autowired EasyAgentActionRepository actions;
 @Autowired EasyAgentMessageRepository messages;
 @Autowired EasyLedgerInquiryService ledger;
 @MockitoBean EasyTutorAccessService access;
 @MockitoBean EasyAgentLlmInterpreter interpreter;
 @Test void chatProposalRequiresApprovalThenExistingJournalEnginePosts() throws Exception{
  EasyBusiness business=businesses.findAll().stream().filter(EasyBusiness::isActive).findFirst().orElseThrow();
  EasyAccount expense=accounts.findAllByActiveTrueAndDirectionOrderBySortOrderAsc(EasyAccountDirection.EXPENSE).stream().findFirst().orElseThrow();
  JwtTokenProvider.AuthenticatedPrincipal principal=new JwtTokenProvider.AuthenticatedPrincipal(10L,"owner-a","TUTOR");
  when(access.requireAllowedBusiness(any(),eq(business.getBusinessId()))).thenReturn(business);
  String csv="거래일,구분,금액,거래처,적요,결제수단,원본계정\n2099-10-14,지출,350000,임대인,월세,계좌이체,미분류\n";
  EasyImportBatchResponse staged=imports.uploadCsv(principal,business.getBusinessId(),"agent.csv",new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
  Long rowId=staged.rows().getFirst().rowId();
  Long before=staged.rows().getFirst().matchedAccountId();
  var session=agent.createSession(principal,business.getBusinessId());
  when(interpreter.interpret(eq("이 거래는 임차료로 바꿔줘"),anyString())).thenReturn(
   new EasyAgentLlmInterpreter.Parsed(EasyAgentLlmInterpreter.Intent.PROPOSE_ACCOUNT,staged.batchId(),rowId,expense.getName(),null));
  EasyAgentDtos.ChatResponse proposal=agent.chat(principal,business.getBusinessId(),session.sessionId(),"이 거래는 임차료로 바꿔줘");
  assertThat(proposal.pendingAction().status()).isEqualTo("PROPOSED");
  EasyAccount unchanged=rows.findById(rowId).orElseThrow().getMatchedAccount();\n  assertThat(unchanged==null?null:unchanged.getAccountId()).isEqualTo(before);
  agent.approve(principal,business.getBusinessId(),proposal.pendingAction().actionId());
  assertThat(rows.findById(rowId).orElseThrow().getMatchedAccount().getAccountId()).isEqualTo(expense.getAccountId());
  when(interpreter.interpret(eq("장부 반영을 준비해 줘"),anyString())).thenReturn(
   new EasyAgentLlmInterpreter.Parsed(EasyAgentLlmInterpreter.Intent.PROPOSE_CONFIRM,staged.batchId(),null,null,null));
  EasyAgentDtos.ChatResponse confirmProposal=agent.chat(principal,business.getBusinessId(),session.sessionId(),"장부 반영을 준비해 줘");
  assertThat(rows.findById(rowId).orElseThrow().getCreatedTxn()).isNull();
  agent.approve(principal,business.getBusinessId(),confirmProposal.pendingAction().actionId());
  assertThat(rows.findById(rowId).orElseThrow().getCreatedTxn()).isNotNull();
  var trial=ledger.trialBalance(business.getBusinessId(),LocalDate.of(2099,10,1),LocalDate.of(2099,10,31));
  assertThat(trial.balanced()).isTrue();
  assertThat(actions.findById(confirmProposal.pendingAction().actionId()).orElseThrow().getStatus()).isEqualTo(EasyAgentActionStatus.APPLIED);
  assertThat(messages.findBySession_SessionIdOrderByMessageIdAsc(session.sessionId())).hasSize(4);
 }
}
