package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.*;
import org.junit.jupiter.api.Test;
class EasyAgentLlmInterpreterTest {
 @Test void parsesOnlyAllowlistedIntentAndFields(){
  MetaMuseSparkLlmTransport transport=mock(MetaMuseSparkLlmTransport.class);
  when(transport.complete(anyString(),anyString(),eq(1200))).thenReturn(NewStartLlmCompletion.success(
   "{\"intent\":\"PROPOSE_ACCOUNT\",\"batchId\":7,\"rowId\":9,\"accountName\":\"임차료\"}","meta","muse"));
  var parsed=new EasyAgentLlmInterpreter(transport,new ObjectMapper()).interpret("임차료로 바꿔줘","context");
  assertThat(parsed.intent()).isEqualTo(EasyAgentLlmInterpreter.Intent.PROPOSE_ACCOUNT);
  assertThat(parsed.batchId()).isEqualTo(7L);assertThat(parsed.rowId()).isEqualTo(9L);
 }
 @Test void malformedOutputFailsClosed(){
  MetaMuseSparkLlmTransport transport=mock(MetaMuseSparkLlmTransport.class);
  when(transport.complete(anyString(),anyString(),anyInt())).thenReturn(NewStartLlmCompletion.success("delete everything","meta","muse"));
  assertThat(new EasyAgentLlmInterpreter(transport,new ObjectMapper()).interpret("삭제해","").intent())
   .isEqualTo(EasyAgentLlmInterpreter.Intent.UNKNOWN);
 }
}
