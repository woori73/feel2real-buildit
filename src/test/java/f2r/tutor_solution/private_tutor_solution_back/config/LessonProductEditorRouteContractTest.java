package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.controller.StudentConversationPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class LessonProductEditorRouteContractTest {

  @Test
  void lessonEditorRoute_resolvesDedicatedTemplate() throws Exception {
    // given
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfig.class);
    context.refresh();
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    try {
      // when & then
      mockMvc.perform(get("/tutor/academy/lessons/editor"))
          .andExpect(status().isOk())
          .andExpect(view().name("tutor/lesson_product_editor"));
    } finally {
      context.close();
    }
  }

  @Configuration
  @EnableWebMvc
  @Import({WebMvcConfig.class, RoleModelInterceptor.class, StudentConversationPageController.class})
  static class TestMvcConfig {

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
