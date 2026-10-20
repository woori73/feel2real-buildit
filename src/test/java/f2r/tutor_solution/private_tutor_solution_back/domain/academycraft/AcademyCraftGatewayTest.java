package f2r.tutor_solution.private_tutor_solution_back.domain.academycraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.Optional;

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

  // ===== P1-b: /agent 정확 경로 5개 =====
  static final String PLAN="11111111-1111-1111-1111-111111111111";
  static final String[][] AGENT_EXACT={{"GET","/api/rea/7/agent/tools"},{"POST","/api/rea/7/agent/execute"},{"GET","/api/rea/7/agent/plans/"+PLAN},
      {"POST","/api/rea/7/agent/plans/"+PLAN+"/approve"},{"POST","/api/rea/7/agent/plans/"+PLAN+"/cancel"}};

  // given 승인된 정확 경로 5개 / when 메서드와 함께 허용 여부를 확인하면 / then 허용된다
  @Test void agentExactPathsAreAllowedWithTheirOwnMethodOnly() {
    for(String[] c:AGENT_EXACT){
      assertTrue(AcademyCraftGateway.allowedPath(c[1],c[0]),c[0]+" "+c[1]);
      for(String m:new String[]{"GET","POST","PUT","DELETE","PATCH","HEAD"})
        if(!m.equals(c[0])) assertFalse(AcademyCraftGateway.allowedPath(c[1],m),m+" "+c[1]);
    }
  }

  // given 경로만 보는 기존 허용 함수 / when 에이전트 경로를 넣으면 / then 메서드를 모르므로 열지 않는다 (기존 호출부 보호)
  @Test void pathOnlyOverloadNeverOpensAgentPaths() {
    for(String[] c:AGENT_EXACT) assertFalse(AcademyCraftGateway.allowedPath(c[1]),c[1]);
  }

  // given 정확 경로에서 벗어난 변형 / when 확인하면 / then 와일드카드처럼 열리지 않고 모두 거부된다
  @Test void agentPathVariantsAreDenied() {
    String B="/api/rea/7/agent";
    for(String p:new String[]{B,B+"/",B+"/tools/",B+"/tools/x",B+"/execute/",B+"/plans",B+"/plans/",B+"/plans/"+PLAN+"/",
        B+"/plans/"+PLAN+"/approve/x",B+"/plans/"+PLAN+"/delete",B+"/plans/not-a-uuid",B+"/plans/not-a-uuid/approve",
        B+"/plans/"+PLAN+"%2f..",B+"/plans/../tools",B+"/../students",B+"/Tools",B+"//tools",B+"/tools;x=1",
        "/api/rea/7/AGENT/tools","/api/rea/07/agent/tools","/api/rea/0/agent/tools","/api/rea/7/agent-evil/tools","/api/rea/7/agentx","/api/rea/auth/agent/tools"})
      for(String m:new String[]{"GET","POST"}) assertFalse(AcademyCraftGateway.allowedPath(p,m),m+" "+p);
  }

  // given 기존 허용 경로 / when 메서드 포함 함수로 확인하면 / then 기존과 같다 (수동 기능 보존)
  @Test void existingPathsUnchangedByMethodAwareOverload() {
    assertTrue(AcademyCraftGateway.allowedPath("/api/rea/7/students","GET"));
    assertTrue(AcademyCraftGateway.allowedPath("/screens/CLS-01-class-list.html","GET"));
    assertFalse(AcademyCraftGateway.allowedPath("/api/rea/auth/login","POST"));
    assertFalse(AcademyCraftGateway.allowedPath("/api/rea/7/members","GET"));
  }

  // given 새 신원 / when 서명 대상 신원을 만들면 / then 프로토콜 버전이 포함되고 JSON 에도 실린다
  @Test void identityCarriesAgentProtocol() throws Exception {
    var identity=AcademyCraftGateway.newIdentity(54L,"TUTOR",12L);
    assertEquals(1,identity.agentProtocol());
    String jsonText=new ObjectMapper().writeValueAsString(identity);
    assertTrue(jsonText.contains("\"agentProtocol\":1"),jsonText);
    assertTrue(jsonText.contains("\"userId\":54"));
  }

  // given 양쪽 저장소가 고정한 기준값 / when 같은 입력으로 서명하면 / then 같은 값이 나온다 (서명 알고리즘 불일치 탐지)
  @Test void signatureMatchesCrossRepositoryGoldenVector() throws Exception {
    byte[] body="{\"command\":\"오늘 결석 학생\",\"idempotencyKey\":\"0b7e7f0e-2f4a-4a4e-9a52-3f2c1d6e9a11\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertEquals("fbf7683638ad79107448a2a18a940a9576024672c497db7c93e1164a8e3680b1",
        AcademyCraftGateway.sign("test-only-shared-secret-with-at-least-32-bytes","POST","/api/rea/7/agent/execute","eyJ1c2VySWQiOjU0fQ",body));
  }

  // ----- 업스트림 호출 이전 단계의 거부 (네트워크 없이 검증) -----
  private AcademyCraftGateway gatewayFor(Role role) throws Exception {
    var users=mock(UserRepository.class);
    when(users.findById(1L)).thenReturn(Optional.of(User.builder().loginId("u").role(role).build()));
    return new AcademyCraftGateway(users,mock(AcademyRepository.class),new ObjectMapper(),"http://127.0.0.1:8082","test-only-shared-secret-with-at-least-32-bytes");
  }
  private MockHttpServletRequest agentPost(String path,String origin,String contentType,byte[] body) {
    var req=new MockHttpServletRequest("POST","/academycraft"+path);
    req.setRequestURI("/academycraft"+path);
    if(origin!=null) req.addHeader("Origin",origin);
    if(contentType!=null) req.setContentType(contentType);
    req.setContent(body);return req;
  }
  private static final JwtTokenProvider.AuthenticatedPrincipal PRINCIPAL=new JwtTokenProvider.AuthenticatedPrincipal(1L,"u","TUTOR");

  // given 로그인한 TUTOR / when 허용되지 않은 에이전트 경로나 메서드를 호출하면 / then 403, 학생·학부모 등 다른 역할도 403
  @Test void agentRequestsRejectedBeforeUpstreamForBadPathMethodOrRole() throws Exception {
    var g=gatewayFor(Role.TUTOR);
    for(String p:new String[]{"/api/rea/7/agent","/api/rea/7/agent/tools/x","/api/rea/7/agent/plans/not-a-uuid/approve"})
      assertThrows(AccessDeniedException.class,()->g.proxy(agentPost(p,"https://feel2real.co.kr","application/json",new byte[0]),PRINCIPAL,new byte[0]),p);
    var get=new MockHttpServletRequest("GET","/academycraft/api/rea/7/agent/execute");get.setRequestURI("/academycraft/api/rea/7/agent/execute");
    assertThrows(AccessDeniedException.class,()->g.proxy(get,PRINCIPAL,null));
    var student=gatewayFor(Role.STUDENT);
    assertThrows(AccessDeniedException.class,()->student.proxy(agentPost("/api/rea/7/agent/execute","https://feel2real.co.kr","application/json",new byte[0]),PRINCIPAL,new byte[0]));
  }

  // given 정확 경로의 쓰기 요청 / when Origin 이 다르거나 JSON 이 아니거나 쿼리·큰 본문이면 / then 업스트림 전에 거부된다
  @Test void agentPostRejectedForOriginContentTypeQueryAndBodySize() throws Exception {
    var g=gatewayFor(Role.TUTOR); String path="/api/rea/7/agent/execute";
    assertThrows(AccessDeniedException.class,()->g.proxy(agentPost(path,"https://evil.example","application/json",new byte[0]),PRINCIPAL,new byte[0]));
    assertThrows(AccessDeniedException.class,()->g.proxy(agentPost(path,null,"application/json",new byte[0]),PRINCIPAL,new byte[0]));
    var wrongType=assertThrows(ResponseStatusException.class,()->g.proxy(agentPost(path,"https://feel2real.co.kr","text/plain",new byte[0]),PRINCIPAL,new byte[0]));
    assertEquals(415,wrongType.getStatusCode().value());
    var noType=assertThrows(ResponseStatusException.class,()->g.proxy(agentPost(path,"https://feel2real.co.kr",null,new byte[0]),PRINCIPAL,new byte[0]));
    assertEquals(415,noType.getStatusCode().value());
    var withQuery=agentPost(path,"https://feel2real.co.kr","application/json",new byte[0]);withQuery.setQueryString("a=1");
    assertEquals(400,assertThrows(ResponseStatusException.class,()->g.proxy(withQuery,PRINCIPAL,new byte[0])).getStatusCode().value());
    byte[] big=new byte[16_385];
    assertEquals(413,assertThrows(ResponseStatusException.class,()->g.proxy(agentPost(path,"https://feel2real.co.kr","application/json",big),PRINCIPAL,big)).getStatusCode().value());
  }
}
