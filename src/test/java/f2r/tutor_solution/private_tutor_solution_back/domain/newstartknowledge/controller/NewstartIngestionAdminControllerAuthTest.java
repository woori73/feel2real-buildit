package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartCuratedReviewIngestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartOfficialReviewIngestService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * NewStart wiki ingest ADMIN API 접근 제어 (무DB, CI 실행).
 * /ingest/official-review 계약: ROLE_ADMIN만 허용, dryRun 기본값 true(쓰기 없음),
 * 익명 401 / 일반 회원 403 — ACTIVE 전환 경로는 이 컨트롤러에 존재하지 않는다.
 */
@WebMvcTest(controllers = NewstartIngestionAdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("NewStart wiki ingest ADMIN API 접근 제어")
class NewstartIngestionAdminControllerAuthTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider jwtTokenProvider;

  @MockitoBean NewstartDataIngestionService ingestionService;
  @MockitoBean NewstartCuratedReviewIngestService curatedReviewIngestService;
  @MockitoBean NewstartOfficialReviewIngestService officialReviewIngestService;

  private String adminToken;
  private String memberToken;

  @BeforeEach
  void auth() {
    adminToken = jwtTokenProvider.issue(1L, "seed-admin", "ADMIN");
    memberToken = jwtTokenProvider.issue(2L, "member", "USER");
  }

  @Test
  @DisplayName("ADMIN dryRun 기본값: plan만 호출하고 ingest는 호출하지 않는다")
  void adminDryRunDefaultsToPlanOnly() throws Exception {
    when(officialReviewIngestService.planOfficialReview(any(Path.class)))
        .thenReturn(new NewstartOfficialReviewIngestService.OfficialIngestPlan(
            81, 81, 0, 3, 1, 0, List.of()));

    mockMvc.perform(post("/api/admin/newstart/wiki/ingest/official-review")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.sourceCount").value(81))
        .andExpect(jsonPath("$.created").value(81))
        .andExpect(jsonPath("$.activeCreated").value(0));

    verify(officialReviewIngestService).planOfficialReview(any(Path.class));
    verify(officialReviewIngestService, never()).ingestOfficialReview(any());
  }

  @Test
  @DisplayName("ADMIN dryRun=false에서만 실제 ingest가 호출된다")
  void adminExplicitWritePath() throws Exception {
    when(officialReviewIngestService.ingestOfficialReview(any(Path.class)))
        .thenReturn(new NewstartOfficialReviewIngestService.IngestReport(81, 0, 3, 1, List.of()));

    mockMvc.perform(post("/api/admin/newstart/wiki/ingest/official-review")
            .param("dryRun", "false")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(false))
        .andExpect(jsonPath("$.created").value(81))
        .andExpect(jsonPath("$.skipped").value(0))
        .andExpect(jsonPath("$.conflictReview").value(3))
        .andExpect(jsonPath("$.failed").value(0))
        .andExpect(jsonPath("$.activeCreated").value(0));

    verify(officialReviewIngestService).ingestOfficialReview(any(Path.class));
    verify(officialReviewIngestService, never()).planOfficialReview(any());
  }

  @Test
  @DisplayName("일반 회원 403 / 익명 401 — 다른 ingest 엔드포인트도 동일")
  void nonAdminRejected() throws Exception {
    mockMvc.perform(post("/api/admin/newstart/wiki/ingest/official-review")
            .param("dryRun", "false")
            .cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", memberToken)))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/newstart/wiki/ingest/official-review"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/admin/newstart/wiki/ingest/cases/repair")
            .param("articlesDir", anyStringPlaceholder()))
        .andExpect(status().isUnauthorized());
  }

  private String anyStringPlaceholder() {
    return "data/newstart-cafe/initial_seed/articles";
  }
}
