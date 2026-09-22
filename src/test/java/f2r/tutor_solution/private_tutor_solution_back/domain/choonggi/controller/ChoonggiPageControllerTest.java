package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChoonggiPageController.class)
class ChoonggiPageControllerTest {
  @Autowired MockMvc mockMvc;
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
}
