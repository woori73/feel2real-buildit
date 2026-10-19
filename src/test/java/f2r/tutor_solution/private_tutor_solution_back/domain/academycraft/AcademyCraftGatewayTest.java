package f2r.tutor_solution.private_tutor_solution_back.domain.academycraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class AcademyCraftGatewayTest {
  @Test void proxyAllowsOnlyAcademyCraftCoreScreensAndBusinessApis() {
    assertTrue(AcademyCraftGateway.allowedPath("/screens/MBR-01-student-list.html"));
    assertTrue(AcademyCraftGateway.allowedPath("/components/auth.js"));
    assertTrue(AcademyCraftGateway.allowedPath("/api/rea/7/students"));
    assertTrue(AcademyCraftGateway.allowedPath("/api/rea/7/notices"));
    assertTrue(AcademyCraftGateway.allowedPath("/api/rea/7/consultations/1/messages"));
    assertTrue(AcademyCraftGateway.allowedPath("/screens/NTC-01-notice-list.html"));
    assertFalse(AcademyCraftGateway.allowedPath("/screens/SELF-01-student.html"));
    for(String path:new String[]{"/api/rea/auth/login","/api/rea/7/members","/actuator/env","/../grammar-items","/screens/AUTH-01-login.html","/screens/ADM-01-academy-settings.html","/api/rea/7/students%2f..","//evil.example"})
      assertFalse(AcademyCraftGateway.allowedPath(path),path);
  }
  @Test void signatureBindsBodyMethodPathAndIdentity() throws Exception {
    String secret="test-only-shared-secret-with-at-least-32-bytes";
    String signature=AcademyCraftGateway.sign(secret,"POST","/api/rea/7/students","identity","body".getBytes());
    assertEquals(signature,AcademyCraftGateway.sign(secret,"POST","/api/rea/7/students","identity","body".getBytes()));
    assertNotEquals(signature,AcademyCraftGateway.sign(secret,"GET","/api/rea/7/students","identity","body".getBytes()));
    assertNotEquals(signature,AcademyCraftGateway.sign(secret,"POST","/api/rea/8/students","identity","body".getBytes()));
    assertNotEquals(signature,AcademyCraftGateway.sign(secret,"POST","/api/rea/7/students","other","body".getBytes()));
    assertNotEquals(signature,AcademyCraftGateway.sign(secret,"POST","/api/rea/7/students","identity","changed".getBytes()));
  }
  @Test void missingSessionCannotReachUpstreamAndExternalUpstreamRejected() throws Exception {
    var users=mock(UserRepository.class);var academies=mock(AcademyRepository.class);
    var gateway=new AcademyCraftGateway(users,academies,new ObjectMapper(),"http://127.0.0.1:8082","");
    assertTrue(gateway.entry(null).contains("login=required"));
    assertThrows(ResponseStatusException.class,()->gateway.proxy(new MockHttpServletRequest("GET","/academycraft/api/rea/7/students"),null,null));
    verifyNoInteractions(users,academies);
    assertThrows(IllegalArgumentException.class,()->new AcademyCraftGateway(users,academies,new ObjectMapper(),"http://external.example:8082",""));
  }
}
