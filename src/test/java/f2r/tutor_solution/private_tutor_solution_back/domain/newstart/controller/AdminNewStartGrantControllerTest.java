package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGrantService.GrantView;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 감사회원 grant 운영 API 계약. 권한은 경로가 결정한다 — 비로그인 401, 일반 회원(STUDENT) 403,
 * ADMIN만 통과. grant가 없는 회원이 이 API로 자기 기능을 열 수 없어야 하므로, 통과 화면에서도
 * service에 넘어간 user_id와 운영자 표식만 검증한다(PII는 응답에 없다).
 */
@WebMvcTest(controllers = AdminNewStartGrantController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    RoleModelInterceptor.class,
    JwtTokenProvider.class,
    CookieUtil.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class AdminNewStartGrantControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartGrantService grantService;

  @Test
  void anonymousListIsRejected() throws Exception {
    mockMvc.perform(get("/api/admin/newstart/grants")).andExpect(status().is4xxClientError());
    verify(grantService, never()).list();
  }

  @Test
  void generalMemberCannotGrantItself() throws Exception {
    Cookie student = new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "naver-student", "STUDENT"));
    mockMvc.perform(post("/api/admin/newstart/grants")
            .cookie(student)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":1}"))
        .andExpect(status().isForbidden());

    verify(grantService, never()).grant(any(), any(), any());
  }

  @Test
  void adminSeesGrantListWithoutPii() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN"));
    when(grantService.list()).thenReturn(List.of(new GrantView(7L, 41L, NewStartCapability.THANKS_MEMBER, "admin")));

    mockMvc.perform(get("/api/admin/newstart/grants").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(41))
        .andExpect(jsonPath("$[0].capability").value("THANKS_MEMBER"))
        .andExpect(jsonPath("$[0].grantedBy").value("admin"));
  }

  @Test
  void adminGrantIsIdempotentPassthrough() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN"));
    when(grantService.grant(eq(41L), any(), eq("admin")))
        .thenReturn(GrantResult.success(new GrantView(7L, 41L, NewStartCapability.THANKS_MEMBER, "admin")));

    mockMvc.perform(post("/api/admin/newstart/grants")
            .cookie(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":41}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.grant.userId").value(41));
  }

  @Test
  void unknownTargetUserIs400WithUserFacingMessage() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN"));
    when(grantService.grant(anyLong(), any(), any())).thenReturn(GrantResult.failure("회원을 찾을 수 없습니다."));

    mockMvc.perform(post("/api/admin/newstart/grants")
            .cookie(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":999999}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("회원을 찾을 수 없습니다."));
  }

  @Test
  void adminRevokeIsRoutedToService() throws Exception {
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(9L, "admin", "ADMIN"));
    when(grantService.revoke(41L, NewStartCapability.THANKS_MEMBER))
        .thenReturn(GrantResult.success(new GrantView(null, 41L, NewStartCapability.THANKS_MEMBER, null)));

    mockMvc.perform(delete("/api/admin/newstart/grants/41")
            .cookie(admin)
            .param("capability", "THANKS_MEMBER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }
}
