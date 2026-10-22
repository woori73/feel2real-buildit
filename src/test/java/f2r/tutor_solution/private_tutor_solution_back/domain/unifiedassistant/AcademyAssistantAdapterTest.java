package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academycraft.AcademyCraftGateway;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
class AcademyAssistantAdapterTest {
  final AcademyCraftGateway gateway=mock(AcademyCraftGateway.class);
  final AcademyAssistantAdapter adapter=new AcademyAssistantAdapter(gateway);
  final AuthenticatedPrincipal p=new AuthenticatedPrincipal(1L,"owner","TUTOR");
  void response(String method,String path,String data) throws Exception {when(gateway.exchange(eq(p),eq(method),eq(path),any())).thenReturn(ResponseEntity.ok(data.getBytes(StandardCharsets.UTF_8)));}
  @Test void givenTeacherOrOtherAcademy_whenMakingWriteSnapshot_thenDenied() throws Exception {
    response("GET","/api/rea/integration/context?academyId=7","{\"academyId\":7,\"role\":\"TEACHER\"}");
    assertThatThrownBy(()->adapter.snapshot(p,7L,"STUDENT_CREATE",AssistantJson.read("{}"))).hasMessageContaining("원장");
    response("GET","/api/rea/integration/context?academyId=7","{\"academyId\":8,\"role\":\"ACADEMY_OWNER\"}");
    assertThatThrownBy(()->adapter.snapshot(p,7L,"STUDENT_CREATE",AssistantJson.read("{}"))).hasMessageContaining("원장");
  }
  @Test void givenStudentCreate_whenReadBackMatches_thenVerifiedWithoutAuthKey() throws Exception {
    response("POST","/api/rea/7/students","{\"id\":3,\"authKey\":\"secret\"}");response("GET","/api/rea/7/students/3","{\"id\":3,\"name\":\"하늘\",\"authKey\":\"secret\"}");
    var d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{}","x");
    var result=adapter.execute(p,d);assertThat(result.path("verified").asBoolean()).isTrue();assertThat(result.toString()).doesNotContain("secret","authKey");
  }
  @Test void givenMismatchedReadBack_whenExecuted_thenReviewRequired() throws Exception {
    response("POST","/api/rea/7/students","{\"id\":3}");response("GET","/api/rea/7/students/3","{\"id\":3,\"name\":\"다른학생\"}");
    var d=new AssistantDraft(1L,"TUTOR",7L,null,"STUDENT_CREATE","{\"name\":\"하늘\"}","{}","x");
    assertThat(adapter.execute(p,d).path("verified").asBoolean()).isFalse();
  }
  @Test void givenConsultationEnvelope_whenExecuted_thenReadsActualConsultationId() throws Exception {
    response("POST","/api/rea/7/consultations","{\"consultation\":{\"id\":8},\"firstMessageId\":9}");
    response("GET","/api/rea/7/consultations/8","{\"id\":8,\"title\":\"상담\",\"messages\":[{\"id\":9,\"content\":\"기록\"}]}");
    var d=new AssistantDraft(1L,"TUTOR",7L,null,"CONSULT_CREATE","{\"studentId\":1,\"title\":\"상담\",\"content\":\"기록\"}","{}","x");
    assertThat(adapter.execute(p,d).path("recordId").asLong()).isEqualTo(8);
  }
}
