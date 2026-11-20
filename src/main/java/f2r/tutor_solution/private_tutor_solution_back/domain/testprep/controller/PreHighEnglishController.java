package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishAttempt;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishAttempt.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore.Round;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
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
  /** 2부(고1 실전 독해 모의고사 18~45번) 화면·API 주소. */
  private static final String PAGE2 = PAGE + "/part2";
  private static final String API2 = API + "/part2";
  private static final MediaType WEBP = MediaType.parseMediaType("image/webp");

  public record Action(String action, UUID attempt, int number, int choice) {}

  private final PreHighEnglishStore store;
  private final PreHighPart2Store part2;
  private final List<String> origins;

  public PreHighEnglishController(PreHighEnglishStore store, PreHighPart2Store part2,
      @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}") List<String> origins) {
    this.store = store;
    this.part2 = part2;
    this.origins = origins;
  }

  @GetMapping(PAGE)
  public String list(Model model, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    model.addAttribute("rounds", store.list());
    model.addAttribute("part2Rounds", part2.list());
    model.addAttribute("loggedIn", principal != null);
    model.addAttribute("canInstall", principal != null && "ADMIN".equals(principal.role()));
    return "prehigh/list";
  }

  @GetMapping(PAGE + "/{round}")
  public String test(@PathVariable int round, Model model) {
    if (store.get(round) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    model.addAttribute("round", round);
    model.addAttribute("part", 1);
    model.addAttribute("api", API + "/" + round);
    model.addAttribute("numbers", List.of());
    model.addAttribute("shared", store.get(round).reading().stream().map(item -> item.passage() != null).toList());
    return "prehigh/test";
  }

  /** 2부 응시 화면: 18~45번 28문항 45분, 이어서 어휘 50문항(문항당 5초). */
  @GetMapping(PAGE2 + "/{round}")
  public String testPart2(@PathVariable int round, Model model) {
    Round content = part2.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    model.addAttribute("round", round);
    model.addAttribute("part", 2);
    model.addAttribute("api", API2 + "/" + round);
    model.addAttribute("numbers", PreHighPart2Store.numbers());
    model.addAttribute("shared", content.reading().stream().map(item -> item.passage() != null).toList());
    return "prehigh/test";
  }

  /** 2부 문제지 PDF 내려받기(로그인 회원). */
  @GetMapping(PAGE2 + "/{round}/paper.pdf")
  public ResponseEntity<byte[]> paperPart2(@PathVariable int round) {
    byte[] pdf = part2.paper(round);
    if (pdf == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    String name = "F2R_고1_실전독해_모의고사_" + round + "회_문제지.pdf";
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header("Content-Disposition", ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
        .body(pdf);
  }

  @PostMapping(API + "/{round}")
  public ResponseEntity<State> act(@PathVariable int round, @RequestBody Action command,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    var content = store.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return run(content, attemptKey(principal, round), PreHighEnglishStore.READING_SECONDS, command, request);
  }

  @PostMapping(API2 + "/{round}")
  public ResponseEntity<State> actPart2(@PathVariable int round, @RequestBody Action command,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request,
      @RequestHeader(value = "Origin", required = false) String origin) {
    checkOrigin(origin);
    var content = part2.get(round);
    if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return run(content, part2Key(principal, round), PreHighPart2Store.READING_SECONDS, command, request);
  }

  private ResponseEntity<State> run(Round content, String key, int readingSeconds, Action command,
      HttpServletRequest request) {
    HttpSession session = request.getSession();
    if (command.action() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    synchronized (session) {
      PreHighEnglishAttempt attempt = (PreHighEnglishAttempt) session.getAttribute(key);
      Instant now = Instant.now();
      if ("start".equals(command.action()) || "restart".equals(command.action())) {
        if (attempt == null || ("start".equals(command.action()) && attempt.round() != content)
            || "restart".equals(command.action()) && attempt.id().equals(command.attempt())
            && attempt.act("state", 0, 0, now).phase() == PreHighEnglishAttempt.Phase.REVIEW) {
          attempt = new PreHighEnglishAttempt(content, now, readingSeconds);
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
    return picture(attemptKey(principal, round), number, part, request);
  }

  @GetMapping(API2 + "/{round}/image/{number}/{part}")
  public ResponseEntity<byte[]> imagePart2(@PathVariable int round, @PathVariable int number, @PathVariable String part,
      @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request) {
    return picture(part2Key(principal, round), number, part, request);
  }

  private ResponseEntity<byte[]> picture(String key, int number, String part, HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    var attempt = session == null ? null : (PreHighEnglishAttempt) session.getAttribute(key);
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

  @PostMapping(value = "/api/admin/pre-high-english/rounds", consumes = MediaType.APPLICATION_JSON_VALUE)
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

  private static String part2Key(AuthenticatedPrincipal principal, int round) {
    return "pre-high-2:" + principal.userId() + ":" + round;
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
