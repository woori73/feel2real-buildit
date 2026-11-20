package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PreHighVocabularyControllerTest {
  private static final String ORIGIN = "https://feel2real.co.kr";
  private final ObjectMapper json = new ObjectMapper();

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void studentCanFinishGradeTwoWithoutChangingGradeOneAttempt() throws Exception {
    // given
    var bank = new PreHighVocabularyBank(json);
    var mvc = MockMvcBuilders.standaloneSetup(new PreHighVocabularyController(bank, List.of(ORIGIN)))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    var student = new AuthenticatedPrincipal(1L, "vocabulary-student", "STUDENT");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(student, "", List.of()));
    var session = new MockHttpSession();
    mvc.perform(get("/naesintest/pre-high/part7/40")).andExpect(status().isOk())
        .andExpect(model().attribute("examTitle", "고2 어휘 모의고사"))
        .andExpect(model().attribute("examApi", "/api/naesintest/pre-high/part7/40"));
    mvc.perform(get("/naesintest/pre-high/part7/41")).andExpect(status().isNotFound());

    // when
    var other = postAction(mvc, session, 4, Map.of("action", "start", "number", 0, "choice", 0));
    var state = postAction(mvc, session, 7, Map.of("action", "start", "number", 0, "choice", 0));
    String attempt = state.get("id").asText();
    assertThat(attempt).isNotEqualTo(other.get("id").asText());
    var questions = bank.get(7, 1);
    for (int i = 0; i < 50; i++) {
      assertThat(state.at("/item/number").asInt()).isEqualTo(i + 1);
      assertThat(state.at("/item/choices").size()).isEqualTo(5);
      assertThat(state.at("/item/seconds").asInt()).isEqualTo(i < 30 ? 5 : 10);
      assertThat(state.at("/item/answer").isMissingNode()).isTrue();
      state = postAction(mvc, session, 7, Map.of("action", "answer", "attempt", attempt,
          "number", i + 1, "choice", questions.get(i).answer()));
    }

    // then
    assertThat(state.at("/score").asInt()).isEqualTo(100);
    assertThat(state.at("/results").size()).isEqualTo(50);
    assertThat(state.at("/results/30/question/translation").asText()).isNotBlank();
    assertThat(state.at("/results/30/completed").asText()).isEqualTo(questions.get(30).completed());
    assertThat(postAction(mvc, session, 4, Map.of("action", "start", "number", 0, "choice", 0)).get("id")).isEqualTo(other.get("id"));
  }

  private JsonNode postAction(MockMvc mvc, MockHttpSession session, int part, Map<String, Object> body)
      throws Exception {
    var result = mvc.perform(post("/api/naesintest/pre-high/part" + part + "/1")
        .session(session).header("Origin", ORIGIN).contentType("application/json")
        .content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
