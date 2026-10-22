package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class AssistantIntentAnalyzerTest {
  final MetaMuseSparkLlmTransport transport=mock(MetaMuseSparkLlmTransport.class);
  final AssistantIntentAnalyzer analyzer=new AssistantIntentAnalyzer(new AssistantCommandParser(),transport);
  @BeforeEach void enable() {ReflectionTestUtils.setField(analyzer,"enabled",true);}
  void response(String text) {when(transport.complete(anyString(),anyString(),anyInt())).thenReturn(NewStartLlmCompletion.success(text,"meta-muse-spark","test-model"));}
  @Test void givenModelInterpretation_whenValid_thenReturnsDraftInputOnly() {
    response("{\"operation\":\"CONSULT_CREATE\",\"input\":{\"studentId\":12,\"title\":\"수업 계획\",\"content\":\"다음 주 복습\"}}");
    var r=analyzer.analyze("학생 ID 12에게 다음 주 복습 상담을 남겨줘");
    assertThat(r.method()).contains("Muse Spark");assertThat(r.suggestion().input()).containsEntry("studentId",12L);
  }
  @Test void givenScopeSmugglingOrInventedIdsOrFractionalIds_whenModelResponds_thenDiscardsModelOutput() {
    for(String input:new String[]{"{\"name\":\"학생\",\"academyId\":8}","{\"studentId\":999,\"name\":\"학생\"}","{\"studentId\":1.5,\"name\":\"학생\"}"}) {
      response("{\"operation\":\"STUDENT_UPDATE\",\"input\":"+input+"}");
      var r=analyzer.analyze("이 자료를 검토해줘");assertThat(r.method()).contains("응답 확인 필요");assertThat(r.suggestion().operation()).isEmpty();
    }
  }
  @Test void givenUnavailableModel_whenAnalyzed_thenUsesExplicitlyLabelledRuleInterpretation() {
    when(transport.complete(anyString(),anyString(),anyInt())).thenReturn(NewStartLlmCompletion.failed("연결 실패",false));
    var r=analyzer.analyze("학생 하늘 등록해줘");assertThat(r.method()).contains("AI 연결");assertThat(r.suggestion().operation()).isEqualTo("STUDENT_CREATE");
  }
  @Test void givenApprovalOrDisabledModel_whenAnalyzed_thenNeverCallsTransport() {
    assertThat(analyzer.analyze("지금 승인하고 실행해").suggestion().operation()).isEmpty();
    ReflectionTestUtils.setField(analyzer,"enabled",false);analyzer.analyze("학생 하늘 등록");verifyNoInteractions(transport);
  }
}
