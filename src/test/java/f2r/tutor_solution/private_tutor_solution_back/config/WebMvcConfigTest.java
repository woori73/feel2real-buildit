package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller.StudentConversationPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.AbstractView;

class WebMvcConfigTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
      "/home, public/home",
      "/register, auth/register",
      "/tutor/academy, tutor/tutor_academy",
      "/tutor/academy/setup, tutor/tutor_academy_setup",
      "/builder, tutor/academy_builder",
      "/academies, public/academies",
      "/academy-posts, public/academy_posts",
      "/academies/sample-academy, public/academy_public",
      "/analyzes, public/analyzes",
      "/analyzes/10, public/analyze_detail",
      "/wiki/learning, public/wiki_learning",
      "/wiki/learning/7, public/wiki_learning_detail",
      "/wiki/board/review, public/wiki_board",
      "/wiki/board/review/new, public/wiki_post_new",
      "/wiki/posts/42, public/wiki_post",
      "/admin/wiki-originals, admin/wiki_originals",
      "/admin/wiki-analysis, admin/wiki_analysis",
      "/lessons, public/lesson_search",
      "/stdt_dashboard, student/stdt_dashboard",
      "/tutor_dashboard, tutor/tutor_dashboard",
      "/ledger, easy/tutor_agent",
      "/conversation/chat, student/conversation_chat",
      "/conversation/tutor-materials, student/conversation_tutor_materials",
      "/conversation/homework, student/conversation_homework",
      "/conversation/report, student/conversation_report",
      "/conversation/review, student/conversation_review",
      "/exam_review, student/exam_review",
      "/admin/login, admin/login",
      "/admin/dashboard, admin/dashboard",
      "/admin/students, admin/students",
      "/admin/members, admin/members",
      "/admin/analytics, admin/analytics",
      "/admin/analyzes, admin/analyzes",
      "/admin/landing, admin/landing",
      "/admin/newstart/jipijigi, admin/newstart_jipijigi"
  })
  @DisplayName("화면 URL을 templates view name으로 매핑한다")
  void page_routes_resolve_to_template_view_names(String endpoint, String viewName) throws Exception {
    // given
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      // when & then
      mockMvc.perform(get(endpoint))
          .andExpect(status().isOk())
          .andExpect(view().name(viewName));
    } finally {
      context.close();
    }
  }


  @org.junit.jupiter.api.Test
  @DisplayName("기존 원장 장부 URL은 /ledger로 영구 리다이렉트한다")
  void tutor_easy_redirects_permanently_to_ledger() throws Exception {
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      mockMvc.perform(get("/tutor/easy"))
          .andExpect(status().isMovedPermanently())
          .andExpect(redirectedUrl("/ledger"));
    } finally {
      context.close();
    }
  }

  @ParameterizedTest(name = "{0} -> /home")
  @org.junit.jupiter.params.provider.ValueSource(strings = {"/login", "/login.html"})
  @DisplayName("일반 로그인 페이지 URL은 /home으로 임시 리다이렉트한다")
  void login_routes_redirect_to_home(String endpoint) throws Exception {
    // given
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      // when & then
      mockMvc.perform(get(endpoint))
          .andExpect(status().isFound())
          .andExpect(redirectedUrl("/home"));
    } finally {
      context.close();
    }
  }

  @org.junit.jupiter.api.Test
  @DisplayName("구 강사 전용 가입 URL은 더 이상 별도 화면으로 매핑하지 않는다")
  void tutor_register_route_is_removed() throws Exception {
    // given
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      // when & then
      mockMvc.perform(get("/tutor/register"))
          .andExpect(status().isNotFound());
    } finally {
      context.close();
    }
  }

  @org.junit.jupiter.api.Test
  @DisplayName("구 /eduholic은 /home으로 301 영구 리다이렉트한다")
  void eduholic_redirects_permanently_to_home() throws Exception {
    // given
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      // when & then
      mockMvc.perform(get("/eduholic"))
          .andExpect(status().isMovedPermanently())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
              .redirectedUrl("/home"));
    } finally {
      context.close();
    }
  }

  @Configuration
  @EnableWebMvc
  @Import({WebMvcConfig.class, RoleModelInterceptor.class, StudentConversationPageController.class})
  static class TestMvcConfig {

    // PublicSiteProperties는 WebMvcConfig의 @EnableConfigurationProperties가 등록한다
    // (프로퍼티 미설정 → baseDomain=null → 리다이렉트 인터셉터 no-op). 여기서 @Bean으로
    // 또 만들면 중복 빈 충돌.
    @Bean
    AcademyPathRedirectInterceptor academyPathRedirectInterceptor(PublicSiteProperties props) {
      @SuppressWarnings("unchecked")
      org.springframework.beans.factory.ObjectProvider<
          f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository>
          repoProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
      return new AcademyPathRedirectInterceptor(repoProvider, props);
    }
    @Bean
    ConversationService conversationService() {
      return org.mockito.Mockito.mock(ConversationService.class);
    }

    @Bean
    ViewResolver viewResolver() {
      return (viewName, locale) -> new AbstractView() {
        @Override
        protected void renderMergedOutputModel(
            Map<String, Object> model,
            HttpServletRequest request,
            HttpServletResponse response) {
        }
      };
    }
  }
}
