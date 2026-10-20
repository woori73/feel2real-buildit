package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** AcademyCraft 사용 도움말 화면(SSR). ADMIN·TUTOR 로그인 필요. */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class HelpPageController {

  private final HelpCatalog catalog;

  @GetMapping("/help/academycraft")
  public String index(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal, Model model) {
    model.addAttribute("docs", catalog.viewable(principal.role()));
    model.addAttribute("role", principal.role());
    return "help/academycraft/index";
  }

  @GetMapping("/help/academycraft/{slug}")
  public String page(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String slug,
      Model model) {
    HelpDoc doc = catalog.find(slug, principal.role())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    model.addAttribute("doc", doc);
    model.addAttribute("html", HelpMarkdown.toHtml(doc.body()));
    model.addAttribute("docs", catalog.viewable(principal.role()));
    model.addAttribute("related", doc.related().stream().flatMap(s -> catalog.find(s, principal.role()).stream()).toList());
    model.addAttribute("role", principal.role());
    return "help/academycraft/page";
  }
}
