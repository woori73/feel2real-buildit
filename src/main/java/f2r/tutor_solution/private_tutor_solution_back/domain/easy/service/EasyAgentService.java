package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAgentDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class EasyAgentService {
 private final EasyTutorAccessService accessService;
 private final EasyWorkspaceService workspaceService;
 private final EasyLedgerInquiryService ledgerService;
 private final EasyTutorImportService importService;
 private final EasyAgentLlmInterpreter interpreter;
 private final EasyAgentSessionRepository sessions;
 private final EasyAgentMessageRepository messages;
 private final EasyAgentActionRepository actions;
 private final EasyImportBatchRepository batches;
 private final EasyImportRowRepository rows;
 private final EasyAccountRepository accounts;
 private final ObjectMapper mapper;

 @Transactional
 public CreateSessionResponse createSession(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId){
  EasyBusiness b=accessService.requireAllowedBusiness(p,businessId);
  EasyAgentSession s=sessions.save(new EasyAgentSession(b,p.userId()));
  return new CreateSessionResponse(s.getSessionId(),b.getBusinessId());
 }
 public SessionResponse session(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,Long sessionId){
  EasyAgentSession s=requireSession(p,businessId,sessionId);
  return new SessionResponse(s.getSessionId(),businessId,messages.findBySession_SessionIdOrderByMessageIdAsc(sessionId).stream().map(MessageResponse::from).toList());
 }
 @Transactional
 public ChatResponse chat(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,Long sessionId,String userText){
  EasyAgentSession s=requireSession(p,businessId,sessionId);
  String text=userText==null?"":userText.strip();
  if(text.isBlank()||text.length()>2000)throw new IllegalArgumentException("메시지는 1~2000자로 입력해 주세요.");
  messages.save(new EasyAgentMessage(s,"USER","TEXT",text,null));
  if(isExplicitApproval(text)){
   EasyAgentAction pending=actions.findFirstBySession_SessionIdAndStatusOrderByActionIdDesc(sessionId,EasyAgentActionStatus.PROPOSED)
    .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"승인할 대기 제안이 없습니다."));
   ActionResponse applied=approve(p,businessId,pending.getActionId());
   return assistant(s,"RESULT","승인한 작업을 장부에 반영했습니다: "+applied.summary(),json(applied),null);
  }
  return handle(s,p,businessId,interpreter.interpret(text,context(s)));
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public ActionResponse approve(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,Long actionId){
  accessService.requireAllowedBusiness(p,businessId);
  EasyAgentAction a=actions.findOwnedForUpdate(actionId,businessId,p.userId())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"승인 대상을 찾을 수 없습니다."));
  requireProposed(a);
  if(a.getExpiresAt().isBefore(LocalDateTime.now())){
   a.markExpired();throw new ResponseStatusException(HttpStatus.CONFLICT,"만료된 제안은 적용할 수 없습니다.");
  }
  if(a.getActionType()==EasyAgentActionType.SELECT_ACCOUNT)applyAccount(p,businessId,a);
  else applyConfirm(p,businessId,a);
  a.markApplied();return ActionResponse.from(a);
 }
 @Transactional
 public ActionResponse reject(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,Long actionId){
  accessService.requireAllowedBusiness(p,businessId);
  EasyAgentAction a=actions.findOwnedForUpdate(actionId,businessId,p.userId())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"거절 대상을 찾을 수 없습니다."));
  requireProposed(a);a.markRejected();return ActionResponse.from(a);
 }
 private ChatResponse handle(EasyAgentSession s,JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,EasyAgentLlmInterpreter.Parsed x){
  return switch(x.intent()){
   case MONTH_SUMMARY->{
    EasyWorkspaceMonthlyResponse r=workspaceService.monthly(businessId,YearMonth.now());
    yield assistant(s,"RESULT",r.month()+" 거래 "+r.ledgerSavedCount()+"건, 수입 "+r.income()+"원, 지출 "+r.expense()+"원이며 검토 대기는 "+r.openReviewCount()+"건입니다.",json(r),null);
   }
   case UNCLASSIFIED->{
    List<EasyImportRowResponse> r=unclassified(businessId);
    yield assistant(s,"RESULT","분류 확인이 필요한 거래 "+r.size()+"건입니다.",json(r),null);
   }
   case PROPOSE_ACCOUNT->proposeAccount(s,p,businessId,x);
   case PROPOSE_CONFIRM->proposeConfirm(s,p,businessId,x);
   case GENERAL_LEDGER->generalLedger(s,businessId,x.accountName());
   case TRIAL_BALANCE->{
    YearMonth m=YearMonth.now();EasyLedgerInquiryService.TrialBalanceReport r=ledgerService.trialBalance(businessId,m.atDay(1),m.atEndOfMonth());
    yield assistant(s,"RESULT",r.balanced()?"시산표가 일치합니다. 차변과 대변은 각각 "+r.totalDebit()+"원입니다.":"시산표 차이가 "+r.difference()+"원입니다. 불일치 분개를 확인해 주세요.",json(r),null);
   }
   case UNKNOWN->assistant(s,"QUESTION",x.explanation()==null||x.explanation().isBlank()?"요청을 구체적으로 알려주세요. 조회, 계정과목 변경 제안, 장부 반영 제안을 도와드릴 수 있습니다.":x.explanation(),null,null);
  };
 }
 private ChatResponse proposeAccount(EasyAgentSession s,JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,EasyAgentLlmInterpreter.Parsed x){
  if(x.rowId()==null||x.accountName()==null)return assistant(s,"QUESTION","변경할 거래와 계정과목을 지정해 주세요.",null,null);
  EasyImportRow row=ownedRow(businessId,x.batchId(),x.rowId());
  if(row.getStatus()!=EasyImportRowStatus.READY||row.getCreatedTxn()!=null)return assistant(s,"QUESTION","이미 변경되었거나 장부에 반영된 거래라 새 제안을 만들 수 없습니다.",null,null);
  EasyAccount account=resolveAccount(x.accountName());
  EasyAgentAction a=actions.save(EasyAgentAction.selectAccount(s,s.getBusiness(),p.userId(),row.getBatch().getBatchId(),row.getRowId(),account.getAccountId(),row.getMatchedAccount()==null?null:row.getMatchedAccount().getAccountId(),row.getStatus().name(),"거래 "+row.getRowId()+"의 계정과목을 '"+account.getName()+"'(으)로 변경",LocalDateTime.now().plusMinutes(30)));
  ActionResponse ar=ActionResponse.from(a);
  return assistant(s,"PROPOSAL",a.getSummary()+" 제안을 만들었습니다. 금액·거래일·원본은 변경하지 않습니다. 승인할까요?",json(ar),ar);
 }
 private ChatResponse proposeConfirm(EasyAgentSession s,JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,EasyAgentLlmInterpreter.Parsed x){
  EasyImportBatch b=x.batchId()==null?batches.findAllByBusiness_BusinessIdOrderByBatchIdDesc(businessId).stream().filter(it->it.getStatus()==EasyImportStatus.STAGED).findFirst().orElse(null):batches.findByBatchIdAndBusiness_BusinessId(x.batchId(),businessId).orElse(null);
  if(b==null)return assistant(s,"QUESTION","장부에 반영할 STAGED 배치가 없습니다.",null,null);
  List<Long> ids=rows.findByBatch_BatchIdAndStatusOrderByRowNoAsc(b.getBatchId(),EasyImportRowStatus.READY).stream().filter(r->r.getCreatedTxn()==null).map(EasyImportRow::getRowId).toList();
  if(ids.isEmpty())return assistant(s,"QUESTION","승인 가능한 READY 거래가 없습니다.",null,null);
  EasyAgentAction a=actions.save(EasyAgentAction.confirmImport(s,s.getBusiness(),p.userId(),b.getBatchId(),json(ids),"배치 "+b.getBatchId()+"의 READY 거래 "+ids.size()+"건을 장부에 반영",LocalDateTime.now().plusMinutes(30)));
  ActionResponse ar=ActionResponse.from(a);
  return assistant(s,"PROPOSAL",a.getSummary()+" 제안을 만들었습니다. 승인 전에는 분개하지 않습니다. 승인할까요?",json(ar),ar);
 }
 private ChatResponse generalLedger(EasyAgentSession s,Long businessId,String name){
  if(name==null||name.isBlank())return assistant(s,"QUESTION","조회할 계정과목을 알려주세요.",null,null);
  EasyAccount a=resolveAccount(name);YearMonth m=YearMonth.now();
  EasyLedgerInquiryService.AccountLedger r=ledgerService.generalLedger(businessId,a.getAccountId(),m.atDay(1),m.atEndOfMonth());
  return assistant(s,"RESULT",a.getName()+" 총계정원장 "+r.rows().size()+"건, 기말잔액 "+r.closingBalance()+"원입니다.",json(r),null);
 }
 private void applyAccount(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,EasyAgentAction a){
  EasyImportRow row=ownedRow(businessId,a.getBatchId(),a.getRowId());
  Long current=row.getMatchedAccount()==null?null:row.getMatchedAccount().getAccountId();
  if(row.getStatus()!=EasyImportRowStatus.READY||row.getCreatedTxn()!=null||!Objects.equals(current,a.getExpectedAccountId())||!Objects.equals(row.getStatus().name(),a.getExpectedRowStatus())){
   a.markStale("거래가 제안 이후 변경되었습니다.");throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 변경된 제안은 적용할 수 없습니다.");
  }
  importService.selectAccount(p,businessId,a.getBatchId(),a.getRowId(),a.getAccountId());
 }
 private void applyConfirm(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,EasyAgentAction a){
  List<Long> ids;
  try{ids=mapper.readValue(a.getRowIdsJson(),new TypeReference<List<Long>>(){});}
  catch(Exception e){a.markFailed("승인 대상 목록을 읽을 수 없습니다.");throw new IllegalStateException("승인 대상 목록을 읽을 수 없습니다.");}
  batches.findByBatchIdAndBusiness_BusinessId(a.getBatchId(),businessId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"배치를 찾을 수 없습니다."));
  List<EasyImportRow> current=rows.findByBatch_BatchIdAndRowIdIn(a.getBatchId(),ids);
  if(current.size()!=ids.size()||current.stream().anyMatch(r->r.getStatus()!=EasyImportRowStatus.READY||r.getCreatedTxn()!=null)){
   a.markStale("승인 대상 거래가 제안 이후 변경되었습니다.");throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 변경된 제안은 적용할 수 없습니다.");
  }
  importService.confirm(p,businessId,a.getBatchId(),ids);
 }
 private List<EasyImportRowResponse> unclassified(Long businessId){
  List<EasyImportRowResponse> out=new ArrayList<>();
  for(EasyImportBatch b:batches.findAllByBusiness_BusinessIdOrderByBatchIdDesc(businessId)){
   for(EasyImportRow r:rows.findByBatch_BatchIdOrderByRowNoAsc(b.getBatchId())){
    if(r.getCreatedTxn()==null&&(r.getMatchedAccount()==null||r.getStatus()==EasyImportRowStatus.ERROR||r.getStatus()==EasyImportRowStatus.PENDING)){
     out.add(EasyImportRowResponse.from(r));if(out.size()==20)return out;
    }
   }
  }return out;
 }
 private EasyImportRow ownedRow(Long businessId,Long batchId,Long rowId){
  if(batchId!=null){
   batches.findByBatchIdAndBusiness_BusinessId(batchId,businessId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 사업체의 배치를 찾을 수 없습니다."));
   return rows.findByBatch_BatchIdAndRowIdIn(batchId,List.of(rowId)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 배치의 거래를 찾을 수 없습니다."));
  }
  return batches.findAllByBusiness_BusinessIdOrderByBatchIdDesc(businessId).stream().map(b->rows.findByBatch_BatchIdAndRowIdIn(b.getBatchId(),List.of(rowId)).stream().findFirst().orElse(null)).filter(Objects::nonNull).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"해당 사업체의 거래를 찾을 수 없습니다."));
 }
 private EasyAccount resolveAccount(String name){
  String target=name.strip();List<EasyAccount> all=accounts.findAllByActiveTrueOrderBySortOrderAsc();
  List<EasyAccount> found=all.stream().filter(a->a.getName().equals(target)).toList();
  if(found.size()!=1)found=all.stream().filter(a->a.getName().contains(target)||target.contains(a.getName())).toList();
  if(found.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"계정과목을 하나로 확정할 수 없습니다. 정확한 계정명을 입력해 주세요.");
  return found.getFirst();
 }
 private EasyAgentSession requireSession(JwtTokenProvider.AuthenticatedPrincipal p,Long businessId,Long sessionId){
  accessService.requireAllowedBusiness(p,businessId);
  return sessions.findBySessionIdAndBusiness_BusinessIdAndOwnerUserId(sessionId,businessId,p.userId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"대화 세션을 찾을 수 없습니다."));
 }
 private String context(EasyAgentSession s){
  List<EasyAgentMessage> recent=new ArrayList<>(messages.findTop20BySession_SessionIdOrderByMessageIdDesc(s.getSessionId()));Collections.reverse(recent);
  return json(Map.of("messages",recent.stream().map(m->Map.of("role",m.getRole(),"content",m.getContent(),"payload",m.getPayloadJson()==null?"":m.getPayloadJson())).toList(),"activeAccounts",accounts.findAllByActiveTrueOrderBySortOrderAsc().stream().map(a->Map.of("id",a.getAccountId(),"name",a.getName())).toList()));
 }
 private ChatResponse assistant(EasyAgentSession s,String kind,String content,String payload,ActionResponse action){
  EasyAgentMessage m=messages.save(new EasyAgentMessage(s,"ASSISTANT",kind,content,payload));return new ChatResponse(MessageResponse.from(m),action);
 }
 private static boolean isExplicitApproval(String text){return text.replaceAll("\\s+","").matches(".*(승인할게|승인해|승인합니다|적용해|반영해).*");}
 private void requireProposed(EasyAgentAction a){if(a.getStatus()!=EasyAgentActionStatus.PROPOSED)throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 처리되었거나 유효하지 않은 제안입니다.");}
 private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("응답을 직렬화할 수 없습니다.",e);}}
}
