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
  // given 반 목록·상세의 '주간 시간표' 메뉴 / when 허용 여부 확인 / then 허용, 쓰기성·미연결 화면은 계속 차단
  @Test void weeklyTimetableMenuIsAllowedWhileOtherScreensStayBlocked() {
    assertTrue(AcademyCraftGateway.allowedPath("/screens/timetable-weekly.html"));
    for(String p:new String[]{"/screens/OPS-03-excel-io.html","/screens/CHAT-01-ai-chat.html","/screens/timetable-weekly.html/../x","/screens/Timetable-weekly.html"})
      assertFalse(AcademyCraftGateway.allowedPath(p),p);
  }

  // given 차단된 화면 / when 안내 페이지 생성 / then 대시보드 복귀·이전·재시도 버튼이 있고 경로 입력을 되돌려 쓰지 않는다
  @Test void blockedScreenPageOffersReturnAndRetry() {
    var r=AcademyCraftGateway.blockedScreenPageForTest();
    assertEquals(403,r.getStatusCode().value());
    String html=new String(r.getBody(),java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(html.contains("OPS-02-dashboard.html")&&html.contains("history.back()")&&html.contains("location.reload()"));
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
