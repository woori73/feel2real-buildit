package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.*;
import java.util.Locale;
import org.springframework.stereotype.Component;
@Component
public class EasyAgentLlmInterpreter {
 public enum Intent {MONTH_SUMMARY,UNCLASSIFIED,PROPOSE_ACCOUNT,PROPOSE_CONFIRM,GENERAL_LEDGER,TRIAL_BALANCE,UNKNOWN}
 public record Parsed(Intent intent,Long batchId,Long rowId,String accountName,String explanation){}
 private static final String SYSTEM="""
 당신은 Easy 장부의 의도 해석기다. 설명이나 실행을 하지 말고 JSON 객체 하나만 반환한다.
 intent 허용값: MONTH_SUMMARY, UNCLASSIFIED, PROPOSE_ACCOUNT, PROPOSE_CONFIRM, GENERAL_LEDGER, TRIAL_BALANCE, UNKNOWN.
 계정 변경 요청은 PROPOSE_ACCOUNT, 장부 반영 요청은 PROPOSE_CONFIRM이다.
 사용자의 승인 의도는 절대 해석하거나 확정하지 말고 UNKNOWN으로 반환한다.
 금액, 거래일, 원본 CSV, 삭제, businessId, 권한을 만들거나 변경하지 않는다.
 가능한 필드: intent,batchId,rowId,accountName,explanation. 모르면 UNKNOWN이며 ID를 추측하지 않는다.
 """;
 private final MetaMuseSparkLlmTransport transport;
 private final ObjectMapper mapper;
 public EasyAgentLlmInterpreter(MetaMuseSparkLlmTransport transport,ObjectMapper mapper){this.transport=transport;this.mapper=mapper;}
 public Parsed interpret(String message,String serverContext){
  NewStartLlmCompletion c=transport.complete(SYSTEM,"서버가 허용한 문맥:\n"+serverContext+"\n사용자 입력:\n"+message,1200);
  if(!c.ok())return new Parsed(Intent.UNKNOWN,null,null,null,c.errorMessage());
  try{
   String json=c.text().strip().replace("\u0060\u0060\u0060json","").replace("\u0060\u0060\u0060","");
   JsonNode root=mapper.readTree(json);
   Intent intent=Intent.valueOf(root.path("intent").asText("UNKNOWN").toUpperCase(Locale.ROOT));
   return new Parsed(intent,longOrNull(root,"batchId"),longOrNull(root,"rowId"),textOrNull(root,"accountName"),textOrNull(root,"explanation"));
  }catch(Exception ignored){
   return new Parsed(Intent.UNKNOWN,null,null,null,"요청을 안전하게 해석하지 못했습니다. 거래나 계정과목을 더 구체적으로 알려주세요.");
  }
 }
 private static Long longOrNull(JsonNode root,String name){JsonNode n=root.get(name);return n==null||n.isNull()||!n.canConvertToLong()?null:n.longValue();}
 private static String textOrNull(JsonNode root,String name){JsonNode n=root.get(name);return n==null||n.isNull()?null:n.asText();}
}
