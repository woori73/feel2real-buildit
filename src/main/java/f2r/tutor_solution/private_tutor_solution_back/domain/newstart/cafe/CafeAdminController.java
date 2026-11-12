package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class CafeAdminController {
  private final CafeService cafe;
  private final CafeProperties config;

  @GetMapping("/admin/newstart/cafe")
  public String page(@AuthenticationPrincipal AuthenticatedPrincipal p, Model model, HttpServletResponse response) {
    CafeService.admin(p);
    response.setHeader("Cache-Control", "no-store");
    // Keep the browser's same-origin form POST Origin header. The OAuth callback
    // separately uses no-referrer so its authorization code cannot leak.
    response.setHeader("Referrer-Policy", "same-origin");
    model.addAttribute("status", cafe.status(p));
    model.addAttribute("history", cafe.history(p));
    return "admin/newstart_cafe";
  }

  @PostMapping("/admin/newstart/cafe/boards")
  public String boards(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Map<String, String> params, HttpServletRequest request) {
    sameOrigin(request);
    var boards = new LinkedHashMap<String, Integer>();
    try { params.forEach((key, value) -> boards.put(key, Integer.valueOf(value))); }
    catch (NumberFormatException e) { throw CafeService.error(400, "seven_unique_board_ids_required"); }
    cafe.saveBoards(p, boards);
    return "redirect:/admin/newstart/cafe?saved=1";
  }

  @PostMapping("/admin/newstart/cafe/connect")
  public ResponseEntity<Void> connect(@AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest request) {
    sameOrigin(request);
    return ResponseEntity.status(303).location(URI.create(cafe.connect(p))).cacheControl(CacheControl.noStore()).build();
  }

  @GetMapping("/admin/newstart/cafe/callback")
  public String callback(@AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam(required = false) String state, @RequestParam(required = false) String code,
      @RequestParam(required = false) String error, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    cafe.callback(p, state, code, error);
    return "redirect:/admin/newstart/cafe?connected=1";
  }

  @PostMapping("/admin/newstart/cafe/disconnect")
  public String disconnect(@AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest request) {
    sameOrigin(request);
    cafe.disconnect(p);
    return "redirect:/admin/newstart/cafe?disconnected=1";
  }

  private void sameOrigin(HttpServletRequest request) {
    if (!config.base().equals(request.getHeader("Origin"))) throw CafeService.error(403, "same_origin_required");
  }

  @ExceptionHandler(ResponseStatusException.class)
  @ResponseBody
  public ResponseEntity<?> failure(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore())
        .body(Map.of("error", Objects.toString(e.getReason(), "cafe_request_failed")));
  }
}
