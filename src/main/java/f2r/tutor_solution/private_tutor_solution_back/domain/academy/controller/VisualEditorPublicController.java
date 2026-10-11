package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * BuildIt 시각 편집기 공개 API.
 *
 * <p>인증 없이 호출되며, 게시된(published) 홈페이지 HTML/CSS만 반환한다.
 * 초안(draft)은 절대 노출하지 않는다.
 *
 * <p>출력되는 HTML/CSS는 {@link VisualEditorService}에서
 * 1단계 Sanitizer로 재검증된 것이다 (defense in depth).
 *
 * <p>renderMode가 "custom"이 아니거나 게시본이 없으면 404를 반환하며,
 * 공개 페이지(academy_public.js)는 이 경우 기존 P0 렌더로 폴백한다.
 */
@RestController
@RequestMapping("/api/public/academy")
@RequiredArgsConstructor
public class VisualEditorPublicController {

  private final VisualEditorService visualEditorService;

  @GetMapping("/{handle}/homepage")
  public ResponseEntity<Map<String, Object>> getHomepage(
      @PathVariable String handle) {
    Optional<VisualEditorService.PublishedHomepage> homepage =
        visualEditorService.getPublishedHomepage(handle);
    if (homepage.isEmpty()) {
      Map<String, Object> response = new HashMap<>();
      response.put("status", 404);
      response.put("message", "게시된 편집기 홈페이지가 없습니다. P0 홈페이지를 사용하세요.");
      response.put("renderMode", VisualEditorService.RENDER_MODE_TEMPLATE);
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("html", homepage.get().html());
    response.put("css", homepage.get().css());
    response.put("renderMode", VisualEditorService.RENDER_MODE_CUSTOM);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(ResponseStatusException e) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", e.getStatusCode().value());
    response.put("message", e.getReason());
    return ResponseEntity.status(e.getStatusCode()).body(response);
  }
}
