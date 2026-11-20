package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighQuestionPdf;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(PreHighQuestionPdfController.API)
public class PreHighQuestionPdfController {
  static final String API = "/api/admin/pre-high-english";
  private final PreHighEnglishStore store;
  private final PreHighGrammarBank grammar;

  public PreHighQuestionPdfController(PreHighEnglishStore store, PreHighGrammarBank grammar) {
    this.store = store;
    this.grammar = grammar;
  }

  @GetMapping("/rounds/{round}/{kind}/{number}.pdf")
  public ResponseEntity<byte[]> roundQuestion(@PathVariable int round, @PathVariable String kind,
      @PathVariable int number, @AuthenticationPrincipal AuthenticatedPrincipal principal) throws IOException {
    requireAdmin(principal);
    var content = store.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

    String location = round + "회 " + number + "번";
    byte[] pdf;
    if ("reading".equals(kind)) {
      var reading = question(content.reading(), number);
      pdf = PreHighQuestionPdf.reading("1부 독해 · " + location, reading.passage(), reading.image());
    } else if ("vocabulary".equals(kind)) {
      var word = question(content.words(), number);
      pdf = PreHighQuestionPdf.choices("1부 어휘 · " + location,
          "다음 단어의 뜻으로 알맞은 것을 고르시오.", word.text(), word.choices());
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return download("prehigh-r" + round + "-" + kind + "-" + number + ".pdf", pdf);
  }

  @GetMapping("/grammar/{chapter}/{number}.pdf")
  public ResponseEntity<byte[]> grammarQuestion(@PathVariable int chapter, @PathVariable int number,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) throws IOException {
    requireAdmin(principal);
    var content = grammar.get(chapter);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var question = question(content.questions(), number);
    byte[] pdf = PreHighQuestionPdf.choices("3부 어법 · CH " + chapter + " · " + number + "번",
        question.instruction(), question.text(), question.choices());
    return download("prehigh-ch" + chapter + "-" + number + ".pdf", pdf);
  }

  private static void requireAdmin(AuthenticatedPrincipal principal) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  private static <T> T question(List<T> questions, int number) {
    if (number < 1 || number > questions.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return questions.get(number - 1);
  }

  private static ResponseEntity<byte[]> download(String filename, byte[] pdf) {
    var disposition = ContentDisposition.attachment().filename(filename).build();
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(pdf);
  }
}
