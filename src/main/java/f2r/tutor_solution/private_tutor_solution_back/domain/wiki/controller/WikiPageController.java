package f2r.tutor_solution.private_tutor_solution_back.domain.wiki.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공개 BuildIt Wiki 허브·전체보기 화면(/wiki, /wiki/originals, /wiki/analysis).
 *
 * <p>이 화면들은 관리자 전용 업로드 UI를 <b>서버에서 조건부 렌더</b>해야 하므로 단순 view-controller
 * 대신 전용 컨트롤러로 둔다. 권한 판정은 오직 서버의 authenticated principal({@link AuthenticationPrincipal})
 * 기준이며, 실제 업로드 API(/api/admin/wiki/**)도 ROLE_ADMIN 으로 다시 게이트된다(UI 숨김에만 의존하지 않음).
 *
 * <p>{@code Cache-Control: private, no-store} + {@code Vary: Cookie} 를 명시해 앞단 캐시/CDN 이
 * 로그인 사용자와 비로그인 사용자에게 같은 HTML(관리자 버튼 유무가 다른)을 공유하지 못하게 한다.
 *
 * <p>게시판 목록/글쓰기/상세(/wiki/board/**, /wiki/posts/**)는 role 이 필요 없어 {@code WebMvcConfig}
 * 의 view-controller 로 그대로 둔다.
 */
@Controller
public class WikiPageController {

  @GetMapping("/wiki")
  public String hub(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model, HttpServletResponse response) {
    return render(model, response, principal, "public/wiki");
  }

  @GetMapping("/wiki/originals")
  public String originals(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model, HttpServletResponse response) {
    return render(model, response, principal, "public/wiki_originals");
  }

  @GetMapping("/wiki/analysis")
  public String analysis(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      Model model, HttpServletResponse response) {
    return render(model, response, principal, "public/wiki_analysis");
  }

  private String render(Model model, HttpServletResponse response,
      JwtTokenProvider.AuthenticatedPrincipal principal, String view) {
    model.addAttribute("wikiAdmin", isAdmin(principal));
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
    return view;
  }

  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }
}
