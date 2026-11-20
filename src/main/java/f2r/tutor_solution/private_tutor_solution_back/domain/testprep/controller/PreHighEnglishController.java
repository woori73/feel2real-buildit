package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishAttempt.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarQuestionPdf;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PreHighEnglishController {
  private static final String PAGE = "/naesintest/pre-high";
  private static final String API = "/api/naesintest/pre-high";
  private static final String ADMIN = "/api/admin/pre-high-english";
  private static final MediaType WEBP = MediaType.parseMediaType("image/webp");

  public record Action(String action, UUID attempt, int number, int choice) {}
  public record GrammarAction(String action, UUID attempt, Integer number, Integer choice) {}

  private final PreHighEnglishStore store;
  private final PreHighGrammarBank grammar;
  private final List<String> origins;

  public PreHighEnglishController(PreHighEnglishStore store, PreHighGrammarBank grammar,
      @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}") List<String> origins) {
    this.store = store;
    this.grammar = grammar;
    this.origins = origins;
  }

  @GetMapping(PAGE)
  public String list(Model model, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    model.addAttribute("rounds", store.list());
    model.addAttribute("grammarChapters", grammar.list());
    model.addAttribute("grammarFinalRounds", grammar.finalRounds());
    model.addAttribute("canInstall", principal != null && "ADMIN".equals(principal.role()));
    return "prehigh/list";
  }

  @GetMapping(PAGE + "/grammar/{exam}")
  public String grammarTest(@PathVariable String exam, Model model,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    var content = grammar.get(exam);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    String firstPart = grammar.firstPartForPreviousChapter(exam);
    if (firstPart != null) return "redirect:" + PAGE + "/grammar/" + firstPart;
    model.addAttribute("examTitle", content.title());
    model.addAttribute("examApi", API + "/grammar/" + exam);
    model.addAttribute("count", content.questions().size());
    model.addAttribute("seconds", PreHighGrammarBank.QUESTION_SECONDS);
    if (principal != null && "ADMIN".equals(principal.role())) {
      model.addAttribute("pdfPath", ADMIN + "/grammar/" + exam + "/questions");
    }
    return "prehigh/grammar";
  }

  @GetMapping(ADMIN + "/grammar/{exam}/questions/{number}/pdf")
  public ResponseEntity<byte[]> grammarQuestionPdf(@PathVariable String exam, @PathVariable int number,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) throws IOException {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    var content = grammar.get(exam);
    if (content == null || number < 1 || number > content.questions().size()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    byte[] pdf = PreHighGrammarQuestionPdf.render(content.title(), number, content.questions().get(number - 1));
    String filename = "F2R-grammar-" + exam + "-Q" + number + ".pdf";
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .body(pdf);
  }

  @PostMapping(API + "/grammar/{exam}")
  public ResponseEntity<PreHighGrammarAttempt.State> grammarAct(@PathVariable String exam, @RequestBody GrammarAction command,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    var content = grammar.get(exam);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (command.action() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    HttpSession session = request.getSession();
    String key = "pre-high-grammar:" + principal.userId() + ":" + exam;
    synchronized (session) {
      var attempt = (PreHighGrammarAttempt) session.getAttribute(key);
      Instant now = Instant.now();
      if ("start".equals(command.action())) {
        if (attempt == null) {
          attempt = new PreHighGrammarAttempt(content, now);
          session.setAttribute(key, attempt);
        }
        return noStore(attempt.act("state", 0, 0, now));
      }
      if (attempt == null || !attempt.id().equals(command.attempt())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT);
      }
      if ("restart".equals(command.action())) {
        if (attempt.finished()) {
          attempt = new PreHighGrammarAttempt(content, now);
          session.setAttribute(key, attempt);
        }
        return noStore(attempt.act("state", 0, 0, now));
      }
      try {
        return noStore(attempt.act(command.action(), Objects.requireNonNullElse(command.number(), 0),
            Objects.requireNonNullElse(command.choice(), 0), now));
      } catch (IllegalArgumentException error) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
      }
    }
  }

  @GetMapping(PAGE + "/{round}")
  public String test(@PathVariable int round, Model model) {
    if (store.get(round) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    model.addAttribute("round", round);
    model.addAttribute("shared", store.get(round).reading().stream().map(item -> item.passage() != null).toList());
    return "prehigh/test";
  }

  @PostMapping(API + "/{round}")
  public ResponseEntity<State> act(@PathVariable int round, @RequestBody Action command,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    var content = store.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    HttpSession session = request.getSession();
    String key = attemptKey(principal, round);
    if (command.action() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    synchronized (session) {
      PreHighEnglishAttempt attempt = (PreHighEnglishAttempt) session.getAttribute(key);
      Instant now = Instant.now();
      if ("start".equals(command.action()) || "restart".equals(command.action())) {
        if (attempt == null || ("start".equals(command.action()) && attempt.round() != content)
            || "restart".equals(command.action()) && attempt.id().equals(command.attempt())
            && attempt.act("state", 0, 0, now).phase() == PreHighEnglishAttempt.Phase.REVIEW) {
          attempt = new PreHighEnglishAttempt(content, now);
          session.setAttribute(key, attempt);
        }
        return noStore(attempt.act("state", 0, 0, now));
      }
      if (attempt == null || !attempt.id().equals(command.attempt())) throw new ResponseStatusException(HttpStatus.CONFLICT);
      try {
        return noStore(attempt.act(command.action(), command.number(), command.choice(), now));
      } catch (IllegalArgumentException error) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
      }
    }
  }

  @GetMapping(API + "/{round}/image/{number}/{part}")
  public ResponseEntity<byte[]> image(@PathVariable int round, @PathVariable int number, @PathVariable String part,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    var attempt = session == null ? null : (PreHighEnglishAttempt) session.getAttribute(attemptKey(principal, round));
    if (attempt == null || number < 1 || number > attempt.round().reading().size()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    var reading = attempt.round().reading().get(number - 1);
    String encoded = switch (part) {
      case "question" -> reading.image();
      case "passage" -> reading.passage();
      default -> null;
    };
    if (encoded == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(WEBP)
        .body(Base64.getDecoder().decode(encoded));
  }

  @PostMapping(value = ADMIN + "/rounds", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PreHighEnglishStore.RoundInfo>> install(HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) throws IOException {
    checkOrigin(origin);
    byte[] contents = request.getInputStream().readNBytes(PreHighEnglishStore.MAX_PACKAGE_BYTES + 1);
    if (contents.length == 0 || contents.length > PreHighEnglishStore.MAX_PACKAGE_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "32MB 이하의 회차 자료를 올려 주세요.");
    }
    try {
      store.install(contents);
    } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회차 자료의 형식을 확인해 주세요.");
    }
    return noStore(store.list());
  }

  private void checkOrigin(String origin) {
    if (origin == null || !origins.contains(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private static String attemptKey(AuthenticatedPrincipal principal, int round) {
    return "pre-high:" + principal.userId() + ":" + round;
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
