package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller.EasyBusinessController;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.controller.NewStartOfficialUpdateAdminController;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.servlet.http.Cookie;
import java.util.List;
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

/**
 * CHOONGGI CREATOR role — full authorization matrix.
 *
 * <pre>
 *                PUBLIC   CHOONGGI ADMIN   GLOBAL ADMIN
 * anonymous       YES          NO              NO
 * STUDENT         YES          NO              NO
 * TUTOR           YES          NO              NO
 * CREATOR         YES          YES             NO
 * ADMIN           YES          YES             YES
 * </pre>
 *
 * CREATOR is a CHOONGGI-scoped role, not global admin: it must reach {@code /choonggi/admin/**}
 * and {@code /api/admin/choonggi/**} exactly like ADMIN, but must be rejected by every other
 * {@code /api/admin/**} route (proven here with one NewStart and one EASY endpoint) exactly like
 * a normal user. Draft/deleted-media role gating inside {@link ChoonggiCreatorService} is covered
 * separately in {@code ChoonggiCreatorContractTest} against the real (unmocked) service.
 */
@DisplayName("CHOONGGI CREATOR role — authorization matrix")
@WebMvcTest(controllers = {
    ChoonggiPageController.class,
    ChoonggiCreatorController.class,
    ChoonggiCreatorController.Api.class,
    NewStartOfficialUpdateAdminController.class,
    EasyBusinessController.class
})
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class ChoonggiCreatorRoleAuthorizationMatrixTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @MockitoBean ChoonggiCreatorService choonggiService;
  @MockitoBean NewStartOfficialUpdateService newStartService;
  @MockitoBean NewStartOfficialUpdateDiscoveryService newStartDiscoveryService;
  @MockitoBean EasyBusinessService easyBusinessService;
  @MockitoBean AcademyRepository academyRepository;

  private static final String PUBLIC_PAGE = "/choonggi";
  private static final String CHOONGGI_ADMIN_PAGE = "/choonggi/admin";
  private static final String CHOONGGI_ADMIN_API = "/api/admin/choonggi/posts";
  private static final String NEWSTART_ADMIN_API = "/api/admin/newstart/updates";
  private static final String EASY_ADMIN_API = "/api/admin/easy/businesses";

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(1L, "user-" + role.toLowerCase(), role));
  }

  private void stubChoonggiWrites() {
    ChoonggiPost fake = ChoonggiPost.draft("EP01", "T", "s-" + System.nanoTime(), "b");
    when(choonggiService.save(any(), any(), any(), any(), any(), any())).thenReturn(fake);
    when(choonggiService.publish(anyLong(), any(), any())).thenReturn(fake);
    doNothing().when(choonggiService).unpublish(anyLong(), any(), any());
    doNothing().when(choonggiService).delete(anyLong(), any(), any());
    doNothing().when(choonggiService).cover(anyLong(), anyLong(), any(), any());
    doNothing().when(choonggiService).moveUp(anyLong(), anyLong(), any(), any());
    doNothing().when(choonggiService).moveDown(anyLong(), anyLong(), any(), any());
    doNothing().when(choonggiService).deleteMedia(anyLong(), anyLong(), any(), any());
  }

  // ── A. public published read — every role, including anonymous ────────────
  @Test
  @DisplayName("A. public CHOONGGI page is readable by everyone")
  void publicReadIsAlwaysOpen() throws Exception {
    mockMvc.perform(get(PUBLIC_PAGE)).andExpect(status().isOk());
    for (String role : List.of("STUDENT", "TUTOR", "CREATOR", "ADMIN")) {
      mockMvc.perform(get(PUBLIC_PAGE).cookie(cookie(role))).andExpect(status().isOk());
    }
  }

  // ── anonymous: CHOONGGI admin PAGE is a login-entry redirect (not a raw 401 error
  // page -- a real user hitting /choonggi/admin cold should land on the login modal, not
  // an unexplained browser error); every admin-shaped API stays a plain 401 -────────────
  @Test
  @DisplayName("anonymous -> login-entry redirect on CHOONGGI admin page")
  void anonymousChoonggiAdminPageRedirectsToLogin() throws Exception {
    mockMvc.perform(get(CHOONGGI_ADMIN_PAGE))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?login=required&returnUrl=%2Fchoonggi%2Fadmin"));
  }

  @Test
  @DisplayName("anonymous -> 401 on CHOONGGI admin API, NewStart admin, EASY admin")
  void anonymousUnauthorized() throws Exception {
    mockMvc.perform(get(CHOONGGI_ADMIN_API)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(NEWSTART_ADMIN_API)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(EASY_ADMIN_API)).andExpect(status().isUnauthorized());
  }

  // ── STUDENT / TUTOR: 403 everywhere admin-shaped, exactly like a stranger ──
  @Test
  @DisplayName("STUDENT -> 403 on CHOONGGI admin, NewStart admin, EASY admin")
  void studentForbidden() throws Exception {
    Cookie student = cookie("STUDENT");
    mockMvc.perform(get(CHOONGGI_ADMIN_PAGE).cookie(student)).andExpect(status().isForbidden());
    mockMvc.perform(get(CHOONGGI_ADMIN_API).cookie(student)).andExpect(status().isForbidden());
    mockMvc.perform(get(NEWSTART_ADMIN_API).cookie(student)).andExpect(status().isForbidden());
    mockMvc.perform(get(EASY_ADMIN_API).cookie(student)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("TUTOR -> 403 on CHOONGGI admin, NewStart admin, EASY admin")
  void tutorForbidden() throws Exception {
    Cookie tutor = cookie("TUTOR");
    mockMvc.perform(get(CHOONGGI_ADMIN_PAGE).cookie(tutor)).andExpect(status().isForbidden());
    mockMvc.perform(get(CHOONGGI_ADMIN_API).cookie(tutor)).andExpect(status().isForbidden());
    mockMvc.perform(get(NEWSTART_ADMIN_API).cookie(tutor)).andExpect(status().isForbidden());
    mockMvc.perform(get(EASY_ADMIN_API).cookie(tutor)).andExpect(status().isForbidden());
  }

  // ── CREATOR: B/C/D/E/F/H all 2xx, but I/J/K (generic /api/admin/**) stay 403 ──
  @Test
  @DisplayName("B/H. CREATOR -> 200 on /choonggi/admin and /api/admin/choonggi/**")
  void creatorAllowedOnChoonggiAdmin() throws Exception {
    Cookie creator = cookie("CREATOR");
    when(choonggiService.adminList(any(), any())).thenReturn(List.of());
    mockMvc.perform(get(CHOONGGI_ADMIN_PAGE).cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(get(CHOONGGI_ADMIN_API).cookie(creator)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("C. CREATOR can create a CHOONGGI post")
  void creatorCanCreatePost() throws Exception {
    stubChoonggiWrites();
    mockMvc.perform(post(CHOONGGI_ADMIN_API).cookie(cookie("CREATOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"T\",\"body\":\"b\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("D. CREATOR can update a CHOONGGI post")
  void creatorCanUpdatePost() throws Exception {
    stubChoonggiWrites();
    mockMvc.perform(put(CHOONGGI_ADMIN_API + "/1").cookie(cookie("CREATOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"T\",\"body\":\"b\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("E. CREATOR can publish/unpublish/delete a CHOONGGI post")
  void creatorCanPublishUnpublishDelete() throws Exception {
    stubChoonggiWrites();
    Cookie creator = cookie("CREATOR");
    mockMvc.perform(post(CHOONGGI_ADMIN_API + "/1/publish").cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(post(CHOONGGI_ADMIN_API + "/1/unpublish").cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(delete(CHOONGGI_ADMIN_API + "/1").cookie(creator)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("F. CREATOR can manage CHOONGGI media (cover/reorder/delete)")
  void creatorCanManageMedia() throws Exception {
    stubChoonggiWrites();
    Cookie creator = cookie("CREATOR");
    mockMvc.perform(post(CHOONGGI_ADMIN_API + "/1/cover/9").cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(post(CHOONGGI_ADMIN_API + "/1/media/9/move-up").cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(post(CHOONGGI_ADMIN_API + "/1/media/9/move-down").cookie(creator)).andExpect(status().isOk());
    mockMvc.perform(delete(CHOONGGI_ADMIN_API + "/1/media/9").cookie(creator)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("I/J. CREATOR -> 403 on generic NewStart admin API (isolation)")
  void creatorForbiddenOnNewStartAdmin() throws Exception {
    mockMvc.perform(get(NEWSTART_ADMIN_API).cookie(cookie("CREATOR"))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("I/K. CREATOR -> 403 on generic EASY admin API (isolation)")
  void creatorForbiddenOnEasyAdmin() throws Exception {
    mockMvc.perform(get(EASY_ADMIN_API).cookie(cookie("CREATOR"))).andExpect(status().isForbidden());
  }

  // ── ADMIN: unchanged — 200 everywhere, including generic /api/admin/** ────
  @Test
  @DisplayName("ADMIN -> 200 on CHOONGGI admin, NewStart admin, EASY admin (unchanged)")
  void adminAllowedEverywhere() throws Exception {
    Cookie admin = cookie("ADMIN");
    when(choonggiService.adminList(any(), any())).thenReturn(List.of());
    when(newStartService.adminUpdates(any(), any())).thenReturn(List.of());
    when(easyBusinessService.listActive()).thenReturn(List.of());
    mockMvc.perform(get(CHOONGGI_ADMIN_PAGE).cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(get(CHOONGGI_ADMIN_API).cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(get(NEWSTART_ADMIN_API).cookie(admin)).andExpect(status().isOk());
    mockMvc.perform(get(EASY_ADMIN_API).cookie(admin)).andExpect(status().isOk());
  }
}
