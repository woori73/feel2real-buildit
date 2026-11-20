package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyAttempt.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighVocabularyBank.Question;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
public final class PreHighVocabularyController {
  private static final String PAGE = "/naesintest/pre-high/part4/{round}";
  private static final String API = "/api/naesintest/pre-high/part4/";

  public record Action(String action, UUID attempt, int number, int choice) {}

  private final PreHighVocabularyBank bank;
  private final List<String> origins;

  public PreHighVocabularyController(PreHighVocabularyBank bank,
      @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}") List<String> origins) {
    this.bank = bank;
    this.origins = origins;
  }

  @GetMapping(PAGE)
  public String test(@PathVariable int round, Model model) {
    questions(round);
    model.addAttribute("round", round);
    model.addAttribute("examApi", API + round);
    return "prehigh/vocabulary";
  }

  @PostMapping(API + "{round}")
  public ResponseEntity<State> act(@PathVariable int round, @RequestBody Action command,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) {
    if (origin == null || !origins.contains(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    if (command.action() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var questions = questions(round);
    var session = request.getSession();
    String key = "pre-high-vocabulary:" + principal.userId() + ":" + round;
    synchronized (session) {
      var attempt = (PreHighVocabularyAttempt) session.getAttribute(key);
      Instant now = Instant.now();
      if ("start".equals(command.action())) {
        if (attempt == null) {
          attempt = new PreHighVocabularyAttempt(questions, now);
          session.setAttribute(key, attempt);
        }
        return noStore(attempt.state(now));
      }
      if (attempt == null || !attempt.id().equals(command.attempt())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT);
      }
      try {
        return switch (command.action()) {
          case "state" -> noStore(attempt.state(now));
          case "answer" -> noStore(attempt.answer(command.number(), command.choice(), now));
          case "restart" -> {
            if (attempt.finished()) {
              attempt = new PreHighVocabularyAttempt(questions, now);
              session.setAttribute(key, attempt);
            }
            yield noStore(attempt.state(now));
          }
          default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
      } catch (IllegalArgumentException error) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
      }
    }
  }

  private List<Question> questions(int round) {
    var questions = bank.get(round);
    if (questions == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return questions;
  }

  private static ResponseEntity<State> noStore(State state) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);
  }
}
