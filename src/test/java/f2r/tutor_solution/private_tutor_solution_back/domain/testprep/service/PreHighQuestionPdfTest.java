package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller.PreHighQuestionPdfController;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class PreHighQuestionPdfTest {
  private static final String WEBP = "UklGRiQAAABXRUJQVlA4TBcAAAAvT8AdAAfQ61L3u/9hABLC//1SRP9TVwA=";

  @Test
  void printsKoreanQuestionAndFiveOptions() throws Exception {
    // given
    var options = List.of("성취하다", "놓치다", "설명하다", "변경하다", "기다리다");

    // when
    byte[] bytes = PreHighQuestionPdf.choices("1부 어휘 · 1회 1번",
        "다음 단어의 뜻으로 알맞은 것을 고르시오.", "achieve", options);

    // then
    try (var pdf = Loader.loadPDF(bytes)) {
      assertEquals(1, pdf.getNumberOfPages());
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("다음 단어의 뜻으로 알맞은 것을 고르시오."));
      assertTrue(text.contains("achieve"));
      assertTrue(text.contains("① 성취하다"));
      assertTrue(text.contains("⑤ 기다리다"));
      assertFalse(text.contains("정답"));
    }
  }

  @Test
  void printsOneImagePerPageWithPassageFirst() throws Exception {
    // given / when
    byte[] bytes = PreHighQuestionPdf.reading("1부 독해 · 11회 5번", WEBP, WEBP);

    // then
    try (var pdf = Loader.loadPDF(bytes)) {
      assertEquals(2, pdf.getNumberOfPages());
      assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
      assertTrue(pdf.getPage(1).getResources().getXObjectNames().iterator().hasNext());
      var first = new PDFTextStripper();
      first.setEndPage(1);
      assertTrue(first.getText(pdf).contains("공통 지문"));
    }
  }

  @Test
  void onlyAnAdministratorCanDownloadAndTheResponseIsAnAttachment() throws Exception {
    // given
    var controller = new PreHighQuestionPdfController(null, new PreHighGrammarBank(new ObjectMapper()));
    var student = new AuthenticatedPrincipal(1L, "student", "STUDENT");
    var admin = new AuthenticatedPrincipal(2L, "admin", "ADMIN");

    // when / then
    assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
        () -> controller.roundQuestion(1, "reading", 1, student)).getStatusCode());
    assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
        () -> controller.grammarQuestion(3, 1, null)).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
        () -> controller.grammarQuestion(3, 0, admin)).getStatusCode());

    var response = controller.grammarQuestion(3, 1, admin);
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    assertEquals("attachment; filename=\"prehigh-ch3-1.pdf\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    try (var pdf = Loader.loadPDF(response.getBody())) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("The invitations ___ by the student council yesterday."));
      assertFalse(text.contains("정답"));
    }
  }
}
