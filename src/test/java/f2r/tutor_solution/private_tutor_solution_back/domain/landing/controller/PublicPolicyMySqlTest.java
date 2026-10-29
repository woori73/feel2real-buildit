package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntakeController;
import f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntakePageController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actual MySQL application: policy rendering must not require the proposed intake schema or enable
 * reception.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "app.exam-intake.enabled=false", "app.exam-intake.bank-public-enabled=false",
      "app.exam-intake.bank-admin-enabled=false", "app.home.exam-seller-confirmed=false",
      "app.exam-intake.email-notifications-enabled=false", "anthropic.enabled=false",
      "buildit.unified-assistant.model-analysis-enabled=false"
    })
class PublicPolicyMySqlTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @Autowired JdbcTemplate jdbc;

  @Test
  void publicPoliciesWorkWithMySqlAndReceptionOff() throws Exception {
    // given: full app with real migrated MySQL and disabled intake; when: read each policy
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class))
        .isPositive();
    for (String path :
        new String[] {
          "/policies",
          "/policies/privacy",
          "/policies/terms",
          "/policies/refunds",
          "/policies/business",
          "/privacy",
          "/exam-service/privacy",
          "/exam-service/terms"
        }) {
      var html =
          mvc.perform(get(path))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      // then: SSR is populated and bank details are never published
      assertThat(html).contains("개인정보").doesNotContain("110-498-250866");
    }
    assertThat(context.getBeansOfType(ExamIntakeController.class)).isEmpty();
    assertThat(context.getBeansOfType(ExamIntakePageController.class)).isEmpty();
  }

  @Test
  void publicPolicyDoesNotGrantAdminAccess() throws Exception {
    // given/when: anonymous reads public policy then requests the admin bank API
    mvc.perform(get("/policies")).andExpect(status().isOk());
    // then: the existing authentication boundary still denies access
    mvc.perform(get("/api/admin/exam-intake/bank")).andExpect(status().isUnauthorized());
  }
}
