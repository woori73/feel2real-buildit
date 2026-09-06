package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloadItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloads;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.RecentMemberItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.PopularDownloadService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.WikiHomeFeedService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse;
import java.time.LocalDateTime;
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

@DisplayName("Wiki 메인 home-feed — 비회원 조회 가능, 개인정보 없음")
@WebMvcTest(controllers = WikiHomeFeedController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class WikiHomeFeedControllerWebMvcTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean WikiHomeFeedService homeFeedService;
  @MockitoBean PopularDownloadService popularDownloadService;
  @MockitoBean AcademyRepository academyRepository;

  @Test
  @DisplayName("GET /api/wiki/home-feed — 익명 200, 회원은 마스킹 표시명 + 가입일까지만")
  void homeFeed_anonymous_noPii() throws Exception {
    when(homeFeedService.homeFeed()).thenReturn(new WikiHomeFeedResponse(
        List.of(),
        List.of(),
        List.of(new RecentMemberItem("김OO", LocalDateTime.now())),
        List.of(new WikiLiveMessageResponse(1L, "박OO", "안녕하세요", LocalDateTime.now())),
        new PopularDownloads("최근 24시간", 24, List.of(
            new PopularDownloadItem(1, 5L, "LEARNING", "모의고사 학습 자료", 2, "WEEKLY 01", 8,
                "NEW", 0, "/wiki/learning/5")))));

    mockMvc.perform(get("/api/wiki/home-feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentMembers[0].displayName").value("김OO"))
        .andExpect(jsonPath("$.recentMembers[0].loginId").doesNotExist())
        .andExpect(jsonPath("$.recentMembers[0].email").doesNotExist())
        .andExpect(jsonPath("$.recentMembers[0].name").doesNotExist())
        .andExpect(jsonPath("$.liveMessages[0].displayName").value("박OO"))
        .andExpect(jsonPath("$.liveMessages[0].userId").doesNotExist())
        .andExpect(jsonPath("$.popularDownloads.periodLabel").value("최근 24시간"))
        .andExpect(jsonPath("$.popularDownloads.items[0].downloadCount").value(8));
  }

  @Test
  @DisplayName("GET /api/wiki/popular-downloads — 익명 200")
  void popularDownloads_anonymous_ok() throws Exception {
    when(popularDownloadService.getPopularDownloads())
        .thenReturn(new PopularDownloads("최근 7일", 168, List.of()));

    mockMvc.perform(get("/api/wiki/popular-downloads"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.periodLabel").value("최근 7일"));
  }
}
