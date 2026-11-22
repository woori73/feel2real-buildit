package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PreHighGrammarQuestionPdfTest {
  private static final String PATH = "/api/admin/pre-high-english/grammar/{exam}/questions/{number}/pdf";
  @TempDir Path temporary;
  private PreHighGrammarBank bank;
  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    var mapper = new ObjectMapper();
    bank = new PreHighGrammarBank(mapper);
    var controller = new PreHighEnglishController(
        new PreHighEnglishStore(mapper, temporary.toString()), new PreHighPart2Store(mapper), null, bank,
        List.of("https://feel2real.co.kr"));
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void administratorCanDownloadOnePrintableQuestionWithoutTheExplanation() throws Exception {
    authenticate("ADMIN");
    var pdf = mvc.perform(get(PATH, "final-10", 20))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"F2R-grammar-final-10-Q20.pdf\""))
        .andReturn().getResponse().getContentAsByteArray();

    try (var document = Loader.loadPDF(pdf)) {
      var question = bank.get("final-10").questions().get(19);
      String contents = new PDFTextStripper().getText(document);
      assertThat(document.getNumberOfPages()).isEqualTo(1);
      assertThat(contents).contains("마무리 종합 모의고사 10회", "20. " + question.instruction(), "①", "②", "③", "④", "⑤");
      var labels = List.of("①", "②", "③", "④", "⑤");
      for (int i = 0; i < labels.size(); i++) {
        assertThat(contents).contains(labels.get(i) + " " + question.choices().get(i));
      }
      assertThat(contents).doesNotContain(question.evidence());
    }
  }

  @Test
  void directPdfRequestsFromStudentsAndTutorsAreForbidden() throws Exception {
    mvc.perform(get(PATH, "1-1", 1)).andExpect(status().isForbidden());
    authenticate("STUDENT");
    mvc.perform(get(PATH, "1-1", 1)).andExpect(status().isForbidden());
    authenticate("TUTOR");
    mvc.perform(get(PATH, "1-1", 1)).andExpect(status().isForbidden());
  }

  @Test
  void anInvalidQuestionNumberReturnsNotFoundForAnAdministrator() throws Exception {
    authenticate("ADMIN");
    mvc.perform(get(PATH, "5-2", 21)).andExpect(status().isNotFound());
  }

  private void authenticate(String role) {
    var principal = new AuthenticatedPrincipal(22L, "exam-pdf-test", role, "");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
  }
}
