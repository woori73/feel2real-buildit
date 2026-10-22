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
  final AssistantIntentAnalyzer analyzer=new AssistantIntentAnalyzer(new AssistantCommandParser(),transport,new AssistantModelBudget(100,100,100,System::currentTimeMillis));
  @BeforeEach void enable() {ReflectionTestUtils.setField(analyzer,"enabled",true);}
  void response(String text) {when(transport.complete(anyString(),anyString(),anyInt())).thenReturn(NewStartLlmCompletion.success(text,"meta-muse-spark","test-model"));}
  @Test void givenModelInterpretation_whenValid_thenReturnsDraftInputOnly() {
    response("{\"operation\":\"CONSULT_CREATE\",\"input\":{\"studentId\":12,\"title\":\"수업 계획\",\"content\":\"다음 주 복습\"}}");
    var r=analyzer.analyze(1L,"학생 ID 12에게 다음 주 복습 상담을 남겨줘",null);
    assertThat(r.method()).contains("Muse Spark");assertThat(r.suggestion().input()).containsEntry("studentId",12L);
  }
  @Test void givenScopeSmugglingOrInventedIdsOrFractionalIds_whenModelResponds_thenDiscardsModelOutput() {
    for(String input:new String[]{"{\"name\":\"학생\",\"academyId\":8}","{\"studentId\":999,\"name\":\"학생\"}","{\"studentId\":1.5,\"name\":\"학생\"}"}) {
      response("{\"operation\":\"STUDENT_UPDATE\",\"input\":"+input+"}");
      var r=analyzer.analyze(1L,"이 자료를 검토해줘",null);assertThat(r.method()).contains("응답 확인 필요");assertThat(r.suggestion().operation()).isEmpty();
    }
  }
  @Test void givenUnavailableModel_whenAnalyzed_thenUsesExplicitlyLabelledRuleInterpretation() {
    when(transport.complete(anyString(),anyString(),anyInt())).thenReturn(NewStartLlmCompletion.failed("연결 실패",false));
    var r=analyzer.analyze(1L,"학생 하늘 등록해줘",null);assertThat(r.method()).contains("AI 연결");assertThat(r.suggestion().operation()).isEqualTo("STUDENT_CREATE");
  }
  @Test void givenApprovalOrDisabledModel_whenAnalyzed_thenNeverCallsTransport() {
    assertThat(analyzer.analyze(1L,"지금 승인하고 실행해",null).suggestion().operation()).isEmpty();
    ReflectionTestUtils.setField(analyzer,"enabled",false);analyzer.analyze(1L,"학생 하늘 등록",null);verifyNoInteractions(transport);
  }
  @Test void givenReadOnlyOrCorrectionIntent_whenAnalyzed_thenModelIsNeverCalled() {
    for(String s:new String[]{"학생 ID 5 출결 조회해줘","공지 작성해줘","학생 ID 5 출결 정정해줘"}) analyzer.analyze(1L,s,null);
    verifyNoInteractions(transport);
  }
  @Test void givenContactNumberOrEmail_whenAnalyzed_thenNotSentToModel() {
    var r=analyzer.analyze(1L,"학생 하늘 등록, 학부모연락처: 010-1234-5678",null);
    assertThat(r.method()).contains("연락처 포함");assertThat(r.suggestion().operation()).isEqualTo("STUDENT_CREATE");
    analyzer.analyze(1L,"학생 하늘 등록 parent@example.com",null);
    verifyNoInteractions(transport);
  }
  @Test void givenModelSuppliedParentPhone_whenValidated_thenOutputDiscarded() {
    response("{\"operation\":\"STUDENT_CREATE\",\"input\":{\"name\":\"하늘\",\"parentPhone\":\"010-0000-0000\"}}");
    assertThat(analyzer.analyze(1L,"학생 하늘 등록해줘",null).method()).contains("응답 확인 필요");
  }
  @Test void givenUserOverMinuteBudget_whenAnalyzed_thenRuleFallbackWithoutModelCall() {
    var limited=new AssistantIntentAnalyzer(new AssistantCommandParser(),transport,new AssistantModelBudget(1,10,3,System::currentTimeMillis));
    ReflectionTestUtils.setField(limited,"enabled",true);
    response("{\"operation\":\"\",\"input\":{}}");
    limited.analyze(1L,"이 자료를 검토해줘",null);
    var second=limited.analyze(1L,"학생 하늘 등록해줘",null);
    assertThat(second.method()).contains("AI 사용 한도 초과");assertThat(second.suggestion().operation()).isEqualTo("STUDENT_CREATE");
    verify(transport,times(1)).complete(anyString(),anyString(),anyInt());
  }
  @Test void givenUploadAllowanceOfOne_whenTwoLinesAnalyzed_thenSecondUsesRulesOnly() {
    var a=new AssistantIntentAnalyzer(new AssistantCommandParser(),transport,new AssistantModelBudget(100,100,1,System::currentTimeMillis));
    ReflectionTestUtils.setField(a,"enabled",true);response("{\"operation\":\"\",\"input\":{}}");
    var allowance=a.newAllowance();
    a.analyze(1L,"첫째 자료",allowance);var second=a.analyze(1L,"학생 하늘 등록",allowance);
    assertThat(second.method()).contains("자료당 AI 분석 횟수 초과");verify(transport,times(1)).complete(anyString(),anyString(),anyInt());
  }
}
