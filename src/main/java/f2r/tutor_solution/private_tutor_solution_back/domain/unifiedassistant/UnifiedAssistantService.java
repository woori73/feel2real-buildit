package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyImportBatchResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class UnifiedAssistantService {
  @org.springframework.beans.factory.annotation.Value("${buildit.unified-assistant.write-enabled:false}")
  private boolean writeEnabled;
  private void requireWrites() { if(!writeEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"통합 비서 업무 실행이 비활성화되어 있습니다."); }
  private final UserRepository users;
  private final AssistantProgressRepository progress;
  private final AssistantDraftRepository drafts;
  private final AssistantAuditRepository audit;
  private final AssistantDraftTransactions transactions;
  private final AcademyAssistantAdapter academy;
  private final EasyTutorAccessService access;
  private final EasyTutorImportService imports;
  private final EasyWorkspaceService workspace;

  private static final Map<String,Set<String>> FIELDS=Map.ofEntries(
      Map.entry("STUDENT_CREATE",Set.of("name","grade","schoolName","parentName","parentPhone")),
      Map.entry("STUDENT_UPDATE",Set.of("studentId","name","grade","schoolName","parentName","parentPhone")),
      Map.entry("CLASS_CREATE",Set.of("name","teacherId","description")),
      Map.entry("CLASS_UPDATE",Set.of("classId","name","teacherId","description")),
      Map.entry("CLASS_ASSIGN",Set.of("classId","studentId")),
      Map.entry("ATTENDANCE_RECORD",Set.of("studentId","eventType","occurredAt","note")),
      Map.entry("CONSULT_CREATE",Set.of("studentId","title","content","type")),
      Map.entry("CONSULT_MESSAGE",Set.of("consultId","content")),
      Map.entry("LEDGER_ACCOUNT",Set.of("batchId","rowId","accountId")));

  public void actor(AuthenticatedPrincipal p) {
    if(p==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    var user=users.findById(p.userId()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    if(!Set.of("ADMIN","TUTOR").contains(p.role()) || !user.getRole().name().equals(p.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }
  public record ProgressView(int step,boolean completed,boolean firstVisit,long version,String role) {}
  @Transactional
  public ProgressView state(AuthenticatedPrincipal p) {
    actor(p); var found=progress.findById(p.userId()); boolean first=found.isEmpty();
    var row=found.orElseGet(()->progress.saveAndFlush(new AssistantProgress(p.userId(),p.role())));
    if(!row.getRole().equals(p.role())) { row.update(p.role(),0,false); progress.flush(); }
    return new ProgressView(row.getStep(),row.isCompleted(),first,row.getVersion(),row.getRole());
  }
  @Transactional
  public ProgressView saveProgress(AuthenticatedPrincipal p,int step,boolean completed,long version) {
    actor(p); if(step<0||step>5||(completed&&step!=5)) throw new IllegalArgumentException("안내 단계를 확인하세요.");
    var row=progress.findById(p.userId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"안내를 다시 불러오세요."));
    if(row.getVersion()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 화면에서 안내가 변경되었습니다. 새로고침하세요.");
    row.update(p.role(),step,completed); progress.flush();
    return new ProgressView(step,completed,false,row.getVersion(),p.role());
  }
  public Map<String,Object> overview(AuthenticatedPrincipal p,Long businessId) { return overview(p,businessId,null); }
  public Map<String,Object> overview(AuthenticatedPrincipal p,Long businessId,Long academyId) {
    actor(p); var result=new LinkedHashMap<String,Object>(); result.put("asOf",Instant.now().toString());
    try { var c=academy.context(p,academyId); result.put("academyContext",c); result.put("academy",academy.request(p,"GET",academy.base(c.path("academyId").asLong())+"/dashboard/stats",null)); }
    catch(ResponseStatusException e) { result.put("academyUnavailable",true); }
    if(businessId!=null) { access.requireAllowedBusiness(p,businessId); result.put("finance",workspace.monthly(businessId,YearMonth.now())); }
    return result;
  }
  public JsonNode catalog(AuthenticatedPrincipal p,String kind,Long academyId) {
    actor(p); var c=academy.context(p,academyId); String path=switch(kind) { case "students","classes","consultations" -> academy.base(c.path("academyId").asLong())+"/"+kind; case "teachers" -> "/api/rea/integration/teachers?academyId="+c.path("academyId").asLong(); default -> throw new IllegalArgumentException("지원하지 않는 목록입니다."); };
    return academy.request(p,"GET",path,null);
  }
  @Transactional
  public AssistantDraft draft(AuthenticatedPrincipal p,Long academyId,Long businessId,String operation,JsonNode input) {
    actor(p); requireWrites();
    if("LEDGER_ENTRY".equals(operation)) {
      if(input==null || !input.isObject()) throw new IllegalArgumentException("거래 내용을 확인하세요.");
      var allowed=Set.of("occurredOn","direction","amount","counterparty","description","payMethod","externalAccount","evidenceType");
      input.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new IllegalArgumentException("허용되지 않은 항목입니다.");});
      LocalDate.parse(input.path("occurredOn").asText());
      if(!Set.of("INCOME","EXPENSE").contains(input.path("direction").asText())||!input.path("amount").isIntegralNumber()||!input.path("amount").canConvertToLong()||input.path("amount").asLong()<=0) throw new IllegalArgumentException("수입/지출 구분과 양수 금액을 확인하세요.");
      String csv="거래일,구분,금액,거래처,적요,결제수단,원본계정,증빙유형\n"+allowedCsv(input)+"\n";
      try { return upload(p,businessId,"assistant-entry.csv",csv.getBytes(StandardCharsets.UTF_8)); } catch(IOException e) {throw new IllegalArgumentException("거래 자료를 읽을 수 없습니다.");}
    }
    if(!FIELDS.containsKey(operation) || input==null || !input.isObject()) throw new IllegalArgumentException("업무와 입력값을 확인하세요.");
    ObjectNode data=(ObjectNode)input.deepCopy();
    data.fieldNames().forEachRemaining(key->{ if(!FIELDS.get(operation).contains(key)) throw new IllegalArgumentException("허용되지 않은 입력 항목: "+key); });
    data.forEach(value->{if(!value.isValueNode() || value.isNull() || value.asText().length()>2000) throw new IllegalArgumentException("입력값은 2000자 이하 단일 값이어야 합니다.");});
    if(AssistantJson.write(data).length()>12000) throw new IllegalArgumentException("초안이 너무 큽니다.");
    JsonNode snapshot;
    if(operation.equals("LEDGER_ACCOUNT")) {
      access.requireAllowedBusiness(p,businessId); academyId=null;
      snapshot=AssistantJson.read(AssistantJson.write(imports.detail(p,businessId,AcademyAssistantAdapter.id(data,"batchId"))));
      AcademyAssistantAdapter.id(data,"rowId"); AcademyAssistantAdapter.id(data,"accountId");
    } else {
      businessId=null; validate(operation,data); snapshot=academy.snapshot(p,academyId,operation,data);
      if(operation.equals("STUDENT_UPDATE")) for(String key:List.of("grade","schoolName")) if(!data.has(key)&&snapshot.path("target").hasNonNull(key)) data.set(key,snapshot.path("target").get(key));
    }
    return saveDraft(p,academyId,businessId,operation,data,data,snapshot);
  }
  @Transactional(rollbackFor=Exception.class)
  public AssistantDraft upload(AuthenticatedPrincipal p,Long businessId,String filename,byte[] csv) throws IOException {
    actor(p); requireWrites(); access.requireAllowedBusiness(p,businessId);
    if(csv.length==0 || csv.length>262144) throw new IllegalArgumentException("CSV는 1~256KB로 올려주세요.");
    // The existing importer preserves raw evidence metadata and performs its existing classification.
    var batch=imports.uploadCsv(p,businessId,filename,new ByteArrayInputStream(csv));
    var data=AssistantJson.read("{\"batchId\":"+batch.batchId()+"}");
    var snapshot=AssistantJson.read(AssistantJson.write(batch));
    return saveDraft(p,null,businessId,"LEDGER_IMPORT",data,snapshot,snapshot);
  }
  private AssistantDraft saveDraft(AuthenticatedPrincipal p,Long a,Long b,String op,JsonNode input,JsonNode preview,JsonNode snapshot) {
    if(drafts.countByOwnerIdAndStatus(p.userId(),"PENDING")>=20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"대기 초안을 처리해 주세요 (최대 20개).");
    var d=drafts.save(new AssistantDraft(p.userId(),p.role(),a,b,op,AssistantJson.write(input),AssistantJson.write(preview),AssistantJson.hash(AssistantJson.write(snapshot))));
    audit.save(new AssistantAudit(p.userId(),d.getId(),"DRAFT_CREATED")); return d;
  }
  public AssistantDraft decide(AuthenticatedPrincipal p,String id,String hash,boolean cancel) {
    actor(p); if(!cancel) requireWrites();
    // Scope is rechecked even on a repeated approval; a former owner cannot replay stored results.
    var original=owned(p,id); scope(p,original);
    var d=transactions.claim(p.userId(),p.role(),id,hash,cancel);
    if(!d.getStatus().equals("EXECUTING")) return d;
    try {
      JsonNode data=AssistantJson.read(d.getPayload()), current;
      if(d.getOperation().startsWith("LEDGER_")) current=AssistantJson.read(AssistantJson.write(imports.detail(p,d.getBusinessId(),data.path("batchId").asLong())));
      else current=academy.snapshot(p,d.getAcademyId(),d.getOperation(),data);
      if(!AssistantJson.hash(AssistantJson.write(current)).equals(d.getFingerprint())) return transactions.finish(p.userId(),id,"INVALIDATED",AssistantJson.write(Map.of("message","초안 이후 대상이 변경되었습니다. 새 초안을 작성하세요.")));
      JsonNode result;
      if(d.getOperation().equals("LEDGER_IMPORT")) {
        var before=imports.detail(p,d.getBusinessId(),data.path("batchId").asLong());
        var ready=before.rows().stream().filter(row->row.status().equals("READY")&&row.createdTxnId()==null).map(row->row.rowId()).toList();
        var after=ready.isEmpty()?before:imports.confirm(p,d.getBusinessId(),before.batchId(),ready);
        var readback=imports.detail(p,d.getBusinessId(),after.batchId());
        long saved=readback.rows().stream().filter(row->row.createdTxnId()!=null).count();
        long duplicate=readback.rows().stream().filter(row->row.status().equals("DUPLICATE")).count();
        boolean verified=readback.rows().stream().filter(row->row.createdTxnId()!=null).allMatch(row->{
          var txn=workspace.transaction(d.getBusinessId(),row.createdTxnId()).transaction();
          return Objects.equals(txn.amount(),row.amount()) && Objects.equals(txn.occurredOn(),row.occurredOn()) && Objects.equals(txn.direction(),row.direction());
        });
        long unclassified=readback.rows().stream().filter(row->row.createdTxnId()!=null&&row.matchedAccountId()==null).count();
        long review=readback.rows().stream().filter(row->!row.status().equals("DUPLICATE")&&(row.createdTxnId()==null||row.matchedAccountId()==null||row.evidenceType()==null||row.evidenceType().equals("NONE"))).count();
        long failed=readback.rows().stream().filter(row->row.status().equals("ERROR")).count();
        result=AssistantJson.read(AssistantJson.write(Map.ofEntries(Map.entry("verified",verified),Map.entry("batchId",after.batchId()),Map.entry("collected",readback.totalRows()),Map.entry("saved",saved),Map.entry("duplicates",duplicate),Map.entry("unclassified",unclassified),Map.entry("classified",saved-unclassified),Map.entry("failed",failed),Map.entry("review",review),Map.entry("message","거래 ID·거래일·금액·수입/지출을 장부에서 재조회했습니다. 검토 수에는 저장된 미분류·증빙 미확인 거래도 포함됩니다."))));
      } else if(d.getOperation().equals("LEDGER_ACCOUNT")) {
        imports.selectAccount(p,d.getBusinessId(),data.path("batchId").asLong(),data.path("rowId").asLong(),data.path("accountId").asLong());
        var after=imports.detail(p,d.getBusinessId(),data.path("batchId").asLong());
        boolean verified=after.rows().stream().anyMatch(row->row.rowId().equals(data.path("rowId").asLong())&&Objects.equals(row.matchedAccountId(),data.path("accountId").asLong()));
        result=AssistantJson.read(AssistantJson.write(Map.of("verified",verified,"batchId",after.batchId(),"message","분류 변경 결과를 재조회했습니다.")));
      } else result=academy.execute(p,d);
      String status=result.path("verified").asBoolean() && result.path("review").asLong()==0?"VERIFIED":"REVIEW_REQUIRED";
      return transactions.finish(p.userId(),id,status,AssistantJson.write(result));
    } catch(Exception e) {
      // An upstream timeout may have committed. Preserve UNKNOWN and do not retry the write.
      return transactions.finish(p.userId(),id,"UNKNOWN",AssistantJson.write(Map.of("message","실행 결과를 확정할 수 없습니다. 기존 업무 화면과 감사로그를 확인하세요. 자동 재실행하지 않습니다.")));
    }
  }
  public List<AssistantDraft> history(AuthenticatedPrincipal p) {
    actor(p); return drafts.findTop30ByOwnerIdOrderByCreatedAtDesc(p.userId()).stream().filter(d->{try{scope(p,d);return true;}catch(Exception e){return false;}}).toList();
  }
  public List<AssistantAudit> audit(AuthenticatedPrincipal p) { actor(p); return audit.findTop50ByOwnerIdOrderByIdDesc(p.userId()); }
  private AssistantDraft owned(AuthenticatedPrincipal p,String id) { return drafts.findById(id).filter(d->d.getOwnerId().equals(p.userId())&&d.getRole().equals(p.role())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
  private void scope(AuthenticatedPrincipal p,AssistantDraft d) {
    if(d.getBusinessId()!=null) access.requireAllowedBusiness(p,d.getBusinessId());
    else academy.owner(p,d.getAcademyId());
  }
  private void validate(String op,ObjectNode data) {
    if(op.equals("STUDENT_CREATE")||op.equals("STUDENT_UPDATE")||op.equals("CLASS_CREATE")) required(data,"name");
    if(op.equals("CLASS_CREATE")) AcademyAssistantAdapter.id(data,"teacherId");
    if(op.equals("ATTENDANCE_RECORD")) {
      if(!Set.of("CHECK_IN","CHECK_OUT","ABSENT","LATE","EARLY_LEAVE").contains(data.path("eventType").asText())) throw new IllegalArgumentException("출결 구분을 선택하세요.");
      if(!data.hasNonNull("occurredAt")) data.put("occurredAt",LocalDateTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0).toString());
      LocalDateTime.parse(data.path("occurredAt").asText());
    }
    if(op.equals("CONSULT_CREATE")) {required(data,"title");required(data,"content");data.put("type","NEWS");}
    if(op.equals("CONSULT_MESSAGE")) required(data,"content");
  }
  private String allowedCsv(JsonNode input) {
    return java.util.stream.Stream.of("occurredOn","direction","amount","counterparty","description","payMethod","externalAccount","evidenceType")
      .map(key->"\""+input.path(key).asText("").replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(","));
  }
  private void required(JsonNode data,String key) { if(data.path(key).asText().isBlank()) throw new IllegalArgumentException(key+"를 입력하세요."); }
}
