package f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import f2r.tutor_solution.private_tutor_solution_back.domain.choonggi.service.ChoonggiCreatorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** Public, read-only CHOONGGI vlog pages. */
@Controller
public class ChoonggiPageController {
  private static final LocalDate MATCH_DATE = LocalDate.of(2026, 9, 30);
  private static final String COMPANY = "---";
  private static final List<Episode> EPISODES = List.of(
      new Episode("why-india", "EP.00", "WHY INDIA?", "COMING SOON"),
      new Episode("goodbye-korea", "EP.01", "GOODBYE KOREA", "UPCOMING"),
      new Episode("hanoi-transit", "EP.02", "6H 30M IN HANOI", "COMING SOON"),
      new Episode("hello-mumbai", "EP.03", "HELLO MUMBAI", "COMING SOON"),
      new Episode("first-days-india", "EP.04", "FIRST DAYS IN INDIA", "COMING SOON"),
      new Episode("welcome", "EP.05", "WELCOME TO " + COMPANY, "COMING SOON"),
      new Episode("tournament-ready", "EP.06", "READY FOR THE TOURNAMENT", "UPCOMING"),
      new Episode("match-day", "EP.07", "MATCH DAY — SEP 30", "UPCOMING"),
      new Episode("after-the-match", "EP.08", "AFTER THE MATCH", "COMING SOON"));

  private final ObjectProvider<ChoonggiCreatorService> creatorProvider;

  public ChoonggiPageController(ObjectProvider<ChoonggiCreatorService> creatorProvider) {
    this.creatorProvider = creatorProvider;
  }

  @GetMapping("/choonggi")
  public String home(Model model, HttpServletResponse response) {
    page(model, response, "home", "CHOONGGI — Korea to India Gamer Vlog");
    model.addAttribute("episodes", EPISODES);
    model.addAttribute("matchStatus", matchStatus(LocalDate.now()));
    ChoonggiCreatorService creator = creatorProvider.getIfAvailable();
    model.addAttribute("publishedPosts", creator == null ? List.of() : creator.published());
    return "choonggi/page";
  }

  @GetMapping("/choonggi/{section:vlog|gamer|india|about}")
  public String section(@PathVariable String section, Model model, HttpServletResponse response) {
    page(model, response, section, section.toUpperCase() + " — CHOONGGI");
    model.addAttribute("episodes", EPISODES);
    model.addAttribute("matchStatus", matchStatus(LocalDate.now()));
    ChoonggiCreatorService creator = creatorProvider.getIfAvailable();
    model.addAttribute("publishedPosts", creator == null ? List.of() : creator.published());
    return "choonggi/page";
  }

  @GetMapping("/choonggi/vlog/{slug}")
  public String episode(@PathVariable String slug, Model model, HttpServletResponse response) {
    ChoonggiCreatorService creator = creatorProvider.getIfAvailable();
    if (creator != null) {
      var published = creator.publicPost(slug);
      if (published.isPresent()) {
        page(model, response, "post", published.get().getTitle() + " — CHOONGGI");
        model.addAttribute("canonical", "https://feel2real.co.kr/choonggi/vlog/" + slug);
        model.addAttribute("post", published.get());
        return "choonggi/page";
      }
    }
    Episode episode = EPISODES.stream().filter(it -> it.slug().equals(slug)).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    page(model, response, "episode", episode.title() + " — CHOONGGI");
    model.addAttribute("canonical", "https://feel2real.co.kr/choonggi/vlog/" + slug);
    model.addAttribute("episode", episode);
    return "choonggi/page";
  }

  private void page(Model model, HttpServletResponse response, String page, String title) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
    String suffix = "home".equals(page) ? "" : "/" + ("episode".equals(page) ? "vlog" : page);
    model.addAttribute("page", page);
    model.addAttribute("pageTitle", title);
    model.addAttribute("description", "CHOONGGI personal gamer vlog and Korea-to-India journey.");
    model.addAttribute("canonical", "https://feel2real.co.kr/choonggi" + suffix);
  }

  static String matchStatus(LocalDate today) {
    if (today.isBefore(MATCH_DATE)) return "UPCOMING";
    if (today.isEqual(MATCH_DATE)) return "MATCH DAY";
    return "RESULT PENDING";
  }

  public record Episode(String slug, String number, String title, String status) {}
}
