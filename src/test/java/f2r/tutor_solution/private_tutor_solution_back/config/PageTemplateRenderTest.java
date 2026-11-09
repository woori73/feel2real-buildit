package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PageTemplateRenderTest {

  @Autowired
  private WebApplicationContext context;

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {
      "/",
      "/home",
      "/register",
      "/stdt_dashboard",
      "/tutor_dashboard",
      "/academies",
      "/academy-posts",
      "/conversation/materials",
      "/conversation/report",
      "/conversation/review",
      "/exam_review",
      "/admin/login",
      "/admin/dashboard",
      "/admin/students"
  })
  @DisplayName("화면 URL은 templates HTML을 렌더링한다")
  void page_routes_render_template_html(String endpoint) throws Exception {
    // given
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    // when & then
    mockMvc.perform(get(endpoint))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<!DOCTYPE html>")));
  }
}
