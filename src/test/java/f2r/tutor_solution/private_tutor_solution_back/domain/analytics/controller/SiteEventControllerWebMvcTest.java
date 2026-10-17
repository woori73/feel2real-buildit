package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service.SiteEventRecordingService;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@DisplayName("SiteEventController — 공개 수집 API: 익명 허용, 쿠키 필요, 대량·속도 제한, Bot·관리자 구분")
@WebMvcTest(controllers = SiteEventController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class SiteEventControllerWebMvcTest {

  private static final String VISITOR = UUID.randomUUID().toString();
  private static final String VIEW = UUID.randomUUID().toString();
  private static final String BODY =
      "{\"page\":\"home\",\"viewId\":\"" + VIEW + "\",\"events\":[{\"type\":\"SECTION_VIEW\",\"target\":\"wiki\",\"seq\":0}]}";

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean SiteEventRecordingService service;
  @MockitoBean SiteEventRateLimiter rateLimiter;
  @MockitoBean AcademyRepository academyRepository;

  @BeforeEach
  void stubs() {
    when(rateLimiter.clientIp(any())).thenReturn("1.2.3.4");
    when(rateLimiter.tryAcquire("1.2.3.4")).thenReturn(true);
  }

  @Test
  @DisplayName("given 방문 쿠키가 있는 익명 when 이벤트를 보내면 then 204이고 V:{쿠키} 방문자로 저장한다(본문 없음)")
  void anonymousWithVisitorCookie_recordsAsVisitor() throws Exception {
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
            .cookie(new Cookie("F2R_VISITOR", VISITOR)))
        .andExpect(status().isNoContent());
    verify(service).record(eq("home"), eq(VIEW), any(), eq("V:" + VISITOR), eq(false), eq(false));
  }

  @Test
  @DisplayName("given 방문 쿠키가 없거나 형식이 틀린 요청 when 보내면 then 204이지만 저장하지 않는다(임의 식별값 불신)")
  void withoutValidVisitorCookie_notRecorded() throws Exception {
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isNoContent());
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
            .cookie(new Cookie("F2R_VISITOR", "not-a-uuid")))
        .andExpect(status().isNoContent());
    verify(service, never()).record(any(), any(), any(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @DisplayName("given Bot User-Agent when 보내면 then Bot으로 표시해 저장한다(통계 제외 대상)")
  void bot_markedAsBot() throws Exception {
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
            .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)"))
        .andExpect(status().isNoContent());
    verify(service).record(eq("home"), eq(VIEW), any(), eq("B:BOT"), eq(true), eq(false));
  }

  @Test
  @DisplayName("given ADMIN 로그인 when 보내면 then U:{id} 방문자·내부 트래픽(internal)으로 표시한다")
  void admin_markedInternal() throws Exception {
    Cookie jwt = new Cookie("ACCESS_TOKEN", tokenProvider.issue(7L, "admin-test", "ADMIN"));
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY).cookie(jwt))
        .andExpect(status().isNoContent());
    verify(service).record(eq("home"), eq(VIEW), any(), eq("U:7"), eq(false), eq(true));
  }

  @Test
  @DisplayName("given 한 요청에 21개 이벤트 when 보내면 then 400 — 대량 전송 차단")
  void tooManyEvents_rejected() throws Exception {
    List<String> events = new ArrayList<>();
    for (int i = 0; i < 21; i++) events.add("{\"type\":\"CLICK\",\"target\":\"nav_contact\",\"seq\":0}");
    String body = "{\"page\":\"home\",\"viewId\":\"" + VIEW + "\",\"events\":[" + String.join(",", events) + "]}";
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(body)
            .cookie(new Cookie("F2R_VISITOR", VISITOR)))
        .andExpect(status().isBadRequest());
    verify(service, never()).record(any(), any(), any(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @DisplayName("given 속도 제한 초과 when 보내면 then 429이고 저장하지 않는다")
  void rateLimited_429() throws Exception {
    when(rateLimiter.tryAcquire("1.2.3.4")).thenReturn(false);
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
            .cookie(new Cookie("F2R_VISITOR", VISITOR)))
        .andExpect(status().isTooManyRequests());
    verify(service, never()).record(any(), any(), any(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @DisplayName("given 본문에 알 수 없는 필드(url·userId) when 보내면 then 무시하고 서버 값만 쓴다")
  void unknownFields_ignored() throws Exception {
    String body = "{\"page\":\"home\",\"viewId\":\"" + VIEW + "\",\"url\":\"http://evil\",\"userId\":999,"
        + "\"events\":[{\"type\":\"CLICK\",\"target\":\"nav_contact\",\"seq\":0,\"userId\":999}]}";
    mockMvc.perform(post("/api/analytics/events").contentType(MediaType.APPLICATION_JSON).content(body)
            .cookie(new Cookie("F2R_VISITOR", VISITOR)))
        .andExpect(status().isNoContent());
    verify(service).record(eq("home"), eq(VIEW), any(), eq("V:" + VISITOR), eq(false), eq(false));
  }
}
