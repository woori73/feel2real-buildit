package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
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
  private final PreHighPart2Store part2;

  public PreHighQuestionPdfController(PreHighEnglishStore store, PreHighPart2Store part2) {
    this.store = store;
    this.part2 = part2;
  }

  @GetMapping("/{series:rounds|part2|part5}/{round}/{kind}/{number}.pdf")
  public ResponseEntity<byte[]> roundQuestion(@PathVariable String series, @PathVariable int round, @PathVariable String kind,
      @PathVariable int number, @AuthenticationPrincipal AuthenticatedPrincipal principal) throws IOException {
    requireAdmin(principal);
    boolean practice = "part2".equals(series);
    boolean grade2 = "part5".equals(series);
    var content = grade2 ? store.getGrade2(round) : practice ? part2.get(round) : store.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

    String part = grade2 ? "5부 " : practice ? "2부 " : "1부 ";
    String location = round + "회 " + number + "번";
    byte[] pdf;
    if ("reading".equals(kind)) {
      int first = practice ? PreHighPart2Store.FIRST_NUMBER : 1;
      if (number < first) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      var reading = question(content.reading(), number - first + 1);
      pdf = PreHighQuestionPdf.reading(part + "독해 · " + location, reading.passage(), reading.image());
    } else if ("vocabulary".equals(kind)) {
      var word = question(content.words(), number);
      pdf = PreHighQuestionPdf.choices(part + "어휘 · " + location,
          "다음 단어의 뜻으로 알맞은 것을 고르시오.", word.text(), word.choices());
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return download("prehigh-" + ("rounds".equals(series) ? "" : series + "-") + "r" + round + "-" + kind + "-" + number + ".pdf", pdf);
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
