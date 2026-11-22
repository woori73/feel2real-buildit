package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart6Store;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class PreHighPart6ControllerTest {
  private static final String ORIGIN = "https://feel2real.co.kr";
  private static final String PAGE = "/naesintest/pre-high/part6/{round}";
  private static final String API = "/api/naesintest/pre-high/part6/{round}";
  private static final String PAPER = "/api/admin/pre-high-english/part6/{round}/paper.pdf";
  private static final String START = "{\"action\":\"start\",\"number\":0,\"choice\":0}";
  @TempDir Path temporary;
  private final ObjectMapper mapper = new ObjectMapper();
  private PreHighPart6Store part6;
  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    part6 = new PreHighPart6Store(mapper);
    var controller = new PreHighEnglishController(new PreHighEnglishStore(mapper, temporary.toString()),
        new PreHighPart2Store(mapper), part6, new PreHighGrammarBank(mapper), List.of(ORIGIN));
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("given 6부 1회, when 응시 화면을 열면, then 2부와 같은 화면에 6부 API 주소와 시험지 번호 18~45를 준다")
  void testPageUsesPart6Api() throws Exception {
    // given
    authenticate("STUDENT");
    // when / then
    mvc.perform(get(PAGE, 1)).andExpect(status().isOk()).andExpect(view().name("prehigh/test"))
        .andExpect(model().attribute("part", 6))
        .andExpect(model().attribute("api", "/api/naesintest/pre-high/part6/1"))
        .andExpect(model().attribute("numbers", PreHighPart2Store.numbers()))
        .andExpect(model().attributeDoesNotExist("pdfGroups"));
  }

  @Test
  @DisplayName("given 실리지 않은 회차, when 6부 화면·API·문제지를 요청하면, then 404")
  void missingRoundIsNotFound() throws Exception {
    // given
    authenticate("ADMIN");
    int missing = PreHighPart6Store.ROUND_COUNT + 1;
    // when / then
    mvc.perform(get(PAGE, missing)).andExpect(status().isNotFound());
    mvc.perform(get(PAGE, 0)).andExpect(status().isNotFound());
    mvc.perform(post(API, missing).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
        .content(START)).andExpect(status().isNotFound());
    mvc.perform(get(PAPER, missing)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given 관리자, when 6부 문제지 PDF를 요청하면, then PDF 파일을 내려받는다")
  void administratorDownloadsPaper() throws Exception {
    // given
    authenticate("ADMIN");
    // when
    byte[] pdf = mvc.perform(get(PAPER, 1)).andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
    // then
    assertThat(pdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
  }

  @Test
  @DisplayName("given 비회원·학생·강사, when 6부 문제지 PDF를 직접 요청하면, then 모두 403으로 막힌다")
  void othersAreForbidden() throws Exception {
    // given / when / then
    mvc.perform(get(PAPER, 1)).andExpect(status().isForbidden());
    authenticate("STUDENT");
    mvc.perform(get(PAPER, 1)).andExpect(status().isForbidden());
    authenticate("TUTOR");
    mvc.perform(get(PAPER, 1)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given 6부 1회 응시, when 독해를 제출하고 어휘 50문항을 마치면, then 28문항 채점 결과에 근거·정답 표시와 해설이 있고 그림은 응시 중에만 내려온다")
  void fullAttemptEndsWithMarkedReview() throws Exception {
    // given
    authenticate("STUDENT");
    var session = new MockHttpSession();
    mvc.perform(get(API + "/image/1/question", 1).session(session)).andExpect(status().isNotFound());
    JsonNode started = act(session, START);
    String attempt = started.path("id").asText();
    int answer = part6.get(1).reading().get(0).answer();
    // when
    byte[] image = mvc.perform(get(API + "/image/1/question", 1).session(session)).andExpect(status().isOk())
        .andExpect(content().contentType("image/webp")).andReturn().getResponse().getContentAsByteArray();
    mvc.perform(get(API + "/image/24/passage", 1).session(session)).andExpect(status().isOk());
    mvc.perform(get(API + "/image/1/passage", 1).session(session)).andExpect(status().isNotFound());
    act(session, command("read-answer", attempt, 1, answer));
    act(session, command("reading-done", attempt, 0, 0));
    JsonNode state = act(session, command("words-start", attempt, 0, 0));
    for (int number = 1; number <= 50; number++) {
      state = act(session, command("word-answer", attempt, number, 1));
    }
    // then
    assertThat(started.path("phase").asText()).isEqualTo("READING");
    assertThat(started.path("remainingMillis").asLong()).isBetween(44 * 60_000L, 45 * 60_000L);
    assertThat(started.path("readingAnswers")).hasSize(28);
    assertThat(started.path("readingResults").isNull()).isTrue();
    assertThat(new String(image, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEBP");
    assertThat(state.path("phase").asText()).isEqualTo("REVIEW");
    assertThat(state.path("wordResults")).hasSize(50);
    JsonNode results = state.path("readingResults");
    assertThat(results).hasSize(28);
    assertThat(results.get(0).path("chosen").asInt()).isEqualTo(answer);
    for (JsonNode result : results) {
      assertThat(result.path("explanation").asText()).startsWith("정답 ");
      assertThat(result.path("marks").findValuesAsText("kind")).contains("evidence", "answer");
    }
  }

  @Test
  @DisplayName("given 6부 문항별 PDF, when 관리자가 41번을 내려받으면, then 6부 표시와 공통 지문이 들어 있고 학생은 막힌다")
  void questionPdfSupportsPart6() throws Exception {
    // given
    var controller = new PreHighQuestionPdfController(null, null, part6);
    var student = new AuthenticatedPrincipal(1L, "student", "STUDENT");
    var admin = new AuthenticatedPrincipal(2L, "admin", "ADMIN");
    // when
    var response = controller.roundQuestion("part6", 1, "reading", 41, admin);
    // then
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .isEqualTo("attachment; filename=\"prehigh-part6-r1-reading-41.pdf\"");
    try (var pdf = Loader.loadPDF(response.getBody())) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text).contains("6부 독해 · 1회 41번", "공통 지문").doesNotContain("정답");
    }
    assertThatThrownBy(() -> controller.roundQuestion("part6", 1, "reading", 41, student))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> controller.roundQuestion("part6", 1, "reading", 17, admin))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  private JsonNode act(MockHttpSession session, String body) throws Exception {
    return mapper.readTree(mvc.perform(post(API, 1).session(session).header("Origin", ORIGIN)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray());
  }

  private static String command(String action, String attempt, int number, int choice) {
    return "{\"action\":\"" + action + "\",\"attempt\":\"" + attempt + "\",\"number\":" + number + ",\"choice\":" + choice + "}";
  }

  private void authenticate(String role) {
    var principal = new AuthenticatedPrincipal(23L, "part6-test", role, "");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
  }
}
