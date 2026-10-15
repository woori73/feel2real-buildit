package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateDetailResponse.ContentState;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 내부 상세 라우트 GET /newstart/updates/{id} — 회원 전용(비회원은 로그인으로 redirect) + 404 경계.
 * 카드가 외부 URL로 이동하던 시절의 "클릭 = 400 Bad Request" 경로가 여기서는 존재하지 않는다.
 */
@WebMvcTest(controllers = NewStartOfficialUpdatesPageController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class NewStartUpdatesPageControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean NewStartOfficialUpdateService service;
  @Autowired JwtTokenProvider tokenProvider;

  private jakarta.servlet.http.Cookie memberCookie() {
    return new jakarta.servlet.http.Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "user2", "STUDENT"));
  }

  private static NewStartOfficialUpdateDetailResponse faqDetail(String verifiedSourceUrl) {
    return new NewStartOfficialUpdateDetailResponse(41L, "NEWSTART_FUND", "새출발기금", "새출발기금 공식 FAQ",
        "도덕적 해이 심사에 대해 설명해주세요.",
        "도덕적 해이 조사 제도 안내 원문입니다.",
        false, ContentState.FULL, null, null, verifiedSourceUrl);
  }

  @Test
  void anonymousIsRedirectedToLoginWithNext() throws Exception {
    mockMvc.perform(get("/newstart/updates/41"))
        .andExpect(status().isFound())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .redirectedUrl("/newstart/login?next=%2Fnewstart%2Fupdates%2F41"));
  }

  @Test
  void memberCanOpenInternalDetail() throws Exception {
    when(service.publicUpdateDetail(41L)).thenReturn(faqDetail(null));

    mockMvc.perform(get("/newstart/updates/41").cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("도덕적 해이 조사 제도 안내 원문입니다.")));
  }

  @Test
  void nonPublishedOrUnknownIdIsNotFound() throws Exception {
    doThrow(new IllegalArgumentException("게시된 공식 업데이트를 찾을 수 없습니다."))
        .when(service).publicUpdateDetail(anyLong());

    mockMvc.perform(get("/newstart/updates/9999").cookie(memberCookie()))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonNumericPathDoesNotHitDetailRoute() throws Exception {
    mockMvc.perform(get("/newstart/updates/abc").cookie(memberCookie()))
        .andExpect(status().isNotFound());
  }
}
