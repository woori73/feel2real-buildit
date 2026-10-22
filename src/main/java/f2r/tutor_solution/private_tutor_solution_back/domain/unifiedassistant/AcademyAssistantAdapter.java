package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import com.fasterxml.jackson.databind.JsonNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academycraft.AcademyCraftGateway;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component @RequiredArgsConstructor
public class AcademyAssistantAdapter {
  /** Only a definite rejection of the write request qualifies; failed readback remains UNKNOWN. */
  static class WriteRejected extends ResponseStatusException {
    WriteRejected(org.springframework.http.HttpStatusCode status) {super(status,"학원 API가 저장 요청을 거부했습니다.");}
  }
  private final AcademyCraftGateway gateway;
  public JsonNode request(AuthenticatedPrincipal p,String method,String path,JsonNode body) {
    try {
      var response=gateway.exchange(p,method,path,body==null?new byte[0]:AssistantJson.write(body).getBytes(StandardCharsets.UTF_8));
      if (!response.getStatusCode().is2xxSuccessful()) {
        if(!method.equals("GET")&&Set.of(400,401,403,404,405,409,422).contains(response.getStatusCode().value())) throw new WriteRejected(response.getStatusCode());
        throw new ResponseStatusException(response.getStatusCode(),"학원 API가 요청을 거부했습니다 ("+response.getStatusCode().value()+"). 기존 화면에서 권한·입력값을 확인하세요.");
      }
      return AssistantJson.read(new String(response.getBody(),StandardCharsets.UTF_8));
    } catch(ResponseStatusException e) { throw e; }
    catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"학원 API 연결 결과를 확인할 수 없습니다."); }
  }
  public JsonNode context(AuthenticatedPrincipal p) { return request(p,"GET","/api/rea/integration/context",null); }
  public JsonNode context(AuthenticatedPrincipal p,Long academyId) {
    return academyId==null?context(p):request(p,"GET","/api/rea/integration/context?academyId="+academyId,null);
  }
  public JsonNode owner(AuthenticatedPrincipal p,Long academyId) {
    var c=context(p,academyId);
    if (academyId==null || c.path("academyId").asLong()!=academyId || !"ACADEMY_OWNER".equals(c.path("role").asText()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"AI 쓰기는 연결된 학원의 원장만 승인할 수 있습니다.");
    return c;
  }
  public String base(Long academyId) { return "/api/rea/"+academyId; }
  public JsonNode snapshot(AuthenticatedPrincipal p,Long a,String op,JsonNode data) {
    var context=owner(p,a);
    var snapshot=new TreeMap<String,Object>(); snapshot.put("context",context);
    String root=base(a);
    if (op.equals("STUDENT_UPDATE")) snapshot.put("target",request(p,"GET",root+"/students/"+id(data,"studentId"),null));
    if (op.startsWith("CLASS_" ) && !op.equals("CLASS_CREATE")) snapshot.put("class",request(p,"GET",root+"/classes/"+id(data,"classId"),null));
    if (op.equals("CLASS_ASSIGN")||op.equals("ATTENDANCE_RECORD")||op.equals("CONSULT_CREATE")) snapshot.put("student",request(p,"GET",root+"/students/"+id(data,"studentId"),null));
    if (op.equals("CONSULT_MESSAGE")) snapshot.put("consult",request(p,"GET",root+"/consultations/"+id(data,"consultId"),null));
    return AssistantJson.read(AssistantJson.write(snapshot));
  }
  public JsonNode execute(AuthenticatedPrincipal p,AssistantDraft d) {
    JsonNode data=AssistantJson.read(d.getPayload()); String op=d.getOperation(),root=base(d.getAcademyId());
    var body=data.deepCopy(); if(body.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode)body).remove(List.of("studentId","classId","consultId"));
    String path; String method="POST";
    switch(op) {
      case "STUDENT_CREATE" -> path=root+"/students";
      case "STUDENT_UPDATE" -> { path=root+"/students/"+id(data,"studentId"); method="PUT"; }
      case "CLASS_CREATE" -> path=root+"/classes";
      case "CLASS_UPDATE" -> { path=root+"/classes/"+id(data,"classId"); method="PUT"; }
      case "CLASS_ASSIGN" -> { path=root+"/classes/"+id(data,"classId")+"/students"; body=AssistantJson.read("{\"studentIds\":["+id(data,"studentId")+"]}"); }
      case "ATTENDANCE_RECORD" -> {path=root+"/attendance/check"; body=data;}
      case "CONSULT_CREATE" -> {path=root+"/consultations"; body=data;}
      case "CONSULT_MESSAGE" -> path=root+"/consultations/"+id(data,"consultId")+"/messages";
      default -> throw new IllegalArgumentException("지원하지 않는 업무입니다.");
    }
    JsonNode response=request(p,method,path,body);
    JsonNode result=op.equals("CONSULT_CREATE")?response.path("consultation"):op.equals("CONSULT_MESSAGE")?response.path("message"):response;
    JsonNode readback;
    if (op.startsWith("STUDENT_")) readback=request(p,"GET",root+"/students/"+result.path("id").asLong(),null);
    else if(op.startsWith("CLASS_")) readback=request(p,"GET",root+"/classes/"+result.path("id").asLong(),null);
    else if(op.equals("ATTENDANCE_RECORD")) readback=request(p,"GET",root+"/attendance/student/"+id(data,"studentId"),null);
    else readback=request(p,"GET",root+"/consultations/"+(op.equals("CONSULT_MESSAGE")?id(data,"consultId"):result.path("id").asLong()),null);
    boolean verified;
    if(op.equals("CLASS_ASSIGN")) { verified=false; for(var student:readback.path("students")) if(student.path("id").asLong()==id(data,"studentId")) verified=true; }
    else if(op.equals("ATTENDANCE_RECORD")) {verified=false; for(var event:readback) if(event.path("id").asLong()==result.path("id").asLong() && event.path("eventType").equals(data.path("eventType"))) verified=true;}
    else if(op.equals("CONSULT_MESSAGE")) {verified=false; for(var msg:readback.path("messages")) if(msg.path("id").asLong()==result.path("id").asLong() && msg.path("content").equals(data.path("content"))) verified=true;}
    else { verified=readback.path("id").asLong()>0; for(var field:List.of("name","grade","schoolName","parentName","parentPhone","teacherId","description","title")) if(data.has(field)) verified &= data.get(field).equals(readback.path(field)); }
    if(op.equals("CONSULT_CREATE")) {
      boolean contentMatches=false; for(var msg:readback.path("messages")) if(msg.path("content").equals(data.path("content"))) contentMatches=true;
      verified &= contentMatches && readback.path("studentId").asLong()==id(data,"studentId");
    }
    // Never persist authentication keys returned by student detail/create APIs.
    return AssistantJson.read(AssistantJson.write(Map.of("verified",verified,"recordId",result.path("id").asLong(),"operation",op,"message",verified?"저장 후 재조회로 확인했습니다.":"API가 응답했으나 재조회 검증이 일치하지 않습니다. 수동 확인이 필요합니다.")));
  }
  static long id(JsonNode data,String field) {
    if(!data.path(field).isIntegralNumber() || !data.path(field).canConvertToLong() || data.path(field).asLong()<=0) throw new IllegalArgumentException(field+"를 선택하세요.");
    return data.path(field).asLong();
  }
}
