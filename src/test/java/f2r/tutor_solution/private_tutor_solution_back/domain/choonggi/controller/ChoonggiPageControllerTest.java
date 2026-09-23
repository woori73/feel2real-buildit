package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.entity.ChoonggiPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChoonggiPageController.class)
class ChoonggiPageControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean ChoonggiCreatorService creatorService;

  @BeforeEach void noCreatorPostsByDefault() {
    when(creatorService.published()).thenReturn(List.of());
  }

  @Test void rendersAllPublicRoutes() throws Exception {
    for (String path : new String[]{"/choonggi","/choonggi/vlog","/choonggi/gamer","/choonggi/india","/choonggi/about","/choonggi/vlog/match-day"})
      mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(view().name("choonggi/page"));
  }
  @Test void unknownEpisodeIs404() throws Exception { mockMvc.perform(get("/choonggi/vlog/fake")).andExpect(status().isNotFound()); }
  @Test void matchStatusNeverInventsResult() {
    assertThat(ChoonggiPageController.matchStatus(LocalDate.of(2026,9,29))).isEqualTo("UPCOMING");
    assertThat(ChoonggiPageController.matchStatus(LocalDate.of(2026,9,30))).isEqualTo("MATCH DAY");
    assertThat(ChoonggiPageController.matchStatus(LocalDate.of(2026,10,1))).isEqualTo("RESULT PENDING");
  }
  @Test void publicHomeNeverExposesFlightNumberOrExactTimes() throws Exception {
    String body = mockMvc.perform(get("/choonggi")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    for (String forbidden : new String[]{"VJ961","VJ907","VietJet","11:05","13:35","20:05","23:40"})
      assertThat(body).as("public home must not expose: " + forbidden).doesNotContain(forbidden);
  }

  @Test void vlogListShowsEachFixedEpisodeOnceWhenNoCreatorPostsExist() throws Exception {
    String body = mockMvc.perform(get("/choonggi/vlog")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(occurrences(body, "GOODBYE KOREA")).isEqualTo(1); // fixed EP.01 placeholder
  }

  @Test void vlogListShowsOnlyTheCreatorPostWhenItCoversAFixedEpisode() throws Exception {
    var post = ChoonggiPost.draft("EP01", "Departure Day Notes", "departure-day-notes", "Real first note");
    post.publish();
    when(creatorService.published()).thenReturn(List.of(post));
    String body = mockMvc.perform(get("/choonggi/vlog")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(occurrences(body, "Departure Day Notes")).isEqualTo(1);
    assertThat(body).as("fixed EP.01 placeholder must be dropped once the real post covers it")
        .doesNotContain("GOODBYE KOREA");
  }

  @Test void vlogListShowsFixedAndCreatorEpisodesSeparatelyWhenDifferent() throws Exception {
    var post = ChoonggiPost.draft("EP03", "Mumbai Arrival Story", "mumbai-arrival-story", "Real note");
    post.publish();
    when(creatorService.published()).thenReturn(List.of(post));
    String body = mockMvc.perform(get("/choonggi/vlog")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(occurrences(body, "Mumbai Arrival Story")).isEqualTo(1); // creator EP.03
    assertThat(occurrences(body, "GOODBYE KOREA")).isEqualTo(1); // fixed EP.01, untouched
    assertThat(body).as("fixed EP.03 placeholder must be dropped once the real post covers it")
        .doesNotContain("HELLO MUMBAI");
  }

  @Test void homeShowsTeamPhotoCompanyAndGame() throws Exception {
    String body = mockMvc.perform(get("/choonggi")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(body).contains("/img/choonggi/honor-of-kings-roster.jpg");
    assertThat(body).contains("YUVIN RECKONING");
    assertThat(body).contains("HONOR OF KINGS");
  }

  @Test void homeLogFeedRendersYoutubeEmbedForPublishedPostWithLink() throws Exception {
    var post = ChoonggiPost.draft("EP01", "Departure Day", "departure-day", "First note");
    post.publish();
    post.setYoutubeVideoId("dQw4w9WgXcQ");
    when(creatorService.published()).thenReturn(List.of(post));
    String body = mockMvc.perform(get("/choonggi")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(body).contains("youtube-nocookie.com/embed/dQw4w9WgXcQ");
    assertThat(body).contains("Departure Day");
  }

  @Test void homeLogFeedRendersTextOnlyPostWithoutCrashing() throws Exception {
    var post = ChoonggiPost.draft("EP01", "Text Only Note", "text-only-note", "Just a short note, no media.");
    post.publish();
    when(creatorService.published()).thenReturn(List.of(post));
    String body = mockMvc.perform(get("/choonggi")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(body).contains("Text Only Note");
    assertThat(body).contains("Just a short note, no media.");
  }

  private static int occurrences(String haystack, String needle) {
    int count = 0, from = 0;
    while ((from = haystack.indexOf(needle, from)) != -1) { count++; from += needle.length(); }
    return count;
  }
}
