package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CoordFactoryController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("제작 현황판 API 권한 — 원장(ADMIN) 전용, 할 수 없는 상태는 409")
class CoordFactorySecurityWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean CoordFactoryService service;

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(10L, "user-a", role));
  }

  @Test
  @DisplayName("given 원장(ADMIN) when 현황판·제작 요청·지시서 then 200·201·200")
  void adminCanUseBoard() throws Exception {
    // given
    Cookie admin = cookie("ADMIN");

    // when & then
    mockMvc.perform(get("/api/admin/coord/factory").cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/coord/factory").cookie(admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\"web-req-0001\",\"track\":\"TOEIC\",\"type\":\"EXPECTED\",\"ranges\":[\"12~45\"]}"))
        .andExpect(status().isCreated());
    mockMvc.perform(get("/api/admin/coord/factory/job-1/brief").cookie(admin)).andExpect(status().isOk());
    verify(service).create(any(), any());
    verify(service).brief(any(), eq("job-1"));
  }

  @Test
  @DisplayName("given 강사(TUTOR)·학생·비회원 when 현황판 API then 403·403·401")
  void othersAreBlocked() throws Exception {
    // given & when & then
    mockMvc.perform(get("/api/admin/coord/factory").cookie(cookie("TUTOR"))).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/coord/factory/job-1/publish/approve").cookie(cookie("STUDENT"))).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/coord/factory")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given 게시 승인·게시됨 표시·내림·취소 when 원장이 부름 then 서비스에 jobId와 값이 그대로 간다")
  void publishEndpointsDelegate() throws Exception {
    // given
    Cookie admin = cookie("ADMIN");

    // when
    mockMvc.perform(post("/api/admin/coord/factory/job-1/publish/approve").cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/coord/factory/job-1/publish/done").cookie(admin).contentType(MediaType.APPLICATION_JSON)
        .content("{\"url\":\"/naesintest/school/singu-m2\"}")).andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/coord/factory/job-1/publish/withdraw").cookie(admin).contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"오탈자\"}")).andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/coord/factory/job-1/cancel").cookie(admin)).andExpect(status().isOk());

    // then
    verify(service).approvePublish(any(), eq("job-1"));
    verify(service).markPublished(any(), eq("job-1"), eq("/naesintest/school/singu-m2"));
    verify(service).withdrawPublish(any(), eq("job-1"), eq("오탈자"));
    verify(service).cancel(any(), eq("job-1"));
  }

  @Test
  @DisplayName("given 검수가 모자란 작업 when 게시 승인 then 409와 안내 문장")
  void conflictReturns409() throws Exception {
    // given
    when(service.approvePublish(any(), eq("job-2"))).thenThrow(new IllegalStateException("검수 통과가 3곳 필요합니다(이 작업은 2곳)."));

    // when & then
    mockMvc.perform(post("/api/admin/coord/factory/job-2/publish/approve").cookie(cookie("ADMIN")))
        .andExpect(status().isConflict()).andExpect(content().string("검수 통과가 3곳 필요합니다(이 작업은 2곳)."));
  }
}
