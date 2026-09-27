package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriCard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartGalmuriService.GalmuriResult;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 갈무리 feed API(설계 §6) 접근 계약 — 읽기는 인증 회원, 쓰기 endpoint는 존재하지 않는다.
 *
 * <p>§7의 "UI 숨김만으로 처리 금지"를 반대 방향으로도 지킨다: 승인 전(REVIEW)·만료 항목은
 * service가 404로 돌려 프론트가 열 수 없게 한다.
 */
@WebMvcTest(controllers = NewStartGalmuriController.class)
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
class NewStartGalmuriControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean NewStartGalmuriService galmuriService;

  private static final GalmuriCard CARD = new GalmuriCard(3L, "채권추심 연락 절차",
      "연락 빈도 제한을 요청할 수 있습니다", "추심 대응", "[사례 참고]", "case", "금융감독원",
      "2026-09-01", null, List.of("추심", "연락"));

  private Cookie memberCookie() {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "student", "STUDENT"));
  }

  @Test
  @DisplayName("given 비로그인 / when 목록·상세 / then 401 + service 미호출")
  void anonymousCannotReadFeed() throws Exception {
    mockMvc.perform(get("/api/newstart/galmuri")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/newstart/galmuri/3")).andExpect(status().isUnauthorized());
    verify(galmuriService, never()).feed(any(), any(), any());
    verify(galmuriService, never()).detail(any());
  }

  @Test
  @DisplayName("given 로그인 회원 / when 목록 / then 라벨·톤이 서버 값 그대로 내린다(프론트 재판정 없음)")
  void memberReadsFeed() throws Exception {
    // 기본 page·size 보정은 service 책임 — controller는 클라이언트 값을 가공 없이 넘긴다.
    when(galmuriService.feed(isNull(), isNull(), isNull()))
        .thenReturn(new GalmuriPage(List.of(CARD), true));

    mockMvc.perform(get("/api/newstart/galmuri").cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(3))
        .andExpect(jsonPath("$.items[0].label").value("[사례 참고]"))
        .andExpect(jsonPath("$.items[0].tone").value("case"))
        .andExpect(jsonPath("$.items[0].tags[0]").value("추심"))
        .andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test
  @DisplayName("given category 파라미터 / when 목록 / then service로 그대로 전달된다")
  void categoryReachesService() throws Exception {
    when(galmuriService.feed(eq("추심 대응"), eq(1), eq(30))).thenReturn(new GalmuriPage(List.of(), false));

    mockMvc.perform(get("/api/newstart/galmuri")
            .param("category", "추심 대응").param("page", "1").param("size", "30")
            .cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("given 미승인·만료 항목 / when 상세 / then 404 + 사용자 안내만(본문 미노출)")
  void unapprovedDetailIs404() throws Exception {
    when(galmuriService.detail(9L)).thenReturn(GalmuriResult.failed("갈무리 항목을 찾을 수 없습니다."));

    mockMvc.perform(get("/api/newstart/galmuri/9").cookie(memberCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.errorMessage").value("갈무리 항목을 찾을 수 없습니다."))
        .andExpect(jsonPath("$.item").doesNotExist());
  }

  @Test
  @DisplayName("given ACTIVE 상세 / when 상세 / then 200 + 본문·원출처 링크")
  void activeDetailReturnsContent() throws Exception {
    when(galmuriService.detail(3L)).thenReturn(GalmuriResult.success(
        new NewStartGalmuriService.GalmuriDetail(3L, CARD.title(), CARD.summary(), "갈무리 본문",
            "추심 대응", "[사례 참고]", "case", "금융감독원", "2026-09-01", "2026-01-01", null,
            List.of("추심"), "https://www.fss.or.kr")));

    mockMvc.perform(get("/api/newstart/galmuri/3").cookie(memberCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.item.content").value("갈무리 본문"))
        .andExpect(jsonPath("$.item.sourceUrl").value("https://www.fss.or.kr"));
  }

  @Test
  @DisplayName("given 쓰기 시도 / when POST /then 405 — 갈무리는 자유 게시판이 아니다(§6)")
  void feedHasNoWritePath() throws Exception {
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/newstart/galmuri")
            .cookie(memberCookie())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"title\":\"x\"}"))
        .andExpect(status().isMethodNotAllowed());
  }
}
