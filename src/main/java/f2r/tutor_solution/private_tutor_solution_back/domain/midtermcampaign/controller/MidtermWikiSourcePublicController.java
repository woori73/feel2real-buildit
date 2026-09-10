package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiLiveUpdateView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service.MidtermWikiSourceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * TUTOR Wiki(Python) HTML 자료를 Midterm LIVE 가 read-only visual 로만 쓰기 위한 <b>연결 API</b>.
 * 경로가 {@code /api/academies/**} 라 {@code SecurityConfig} 의 GET permitAll 을 탄다 —
 * 노출 범위는 필터가 아니라 {@link MidtermWikiSourceService} 가 판정한다(발행 캠페인 +
 * 발행 school collection 컨텍스트 정확 일치).
 *
 * <p>공개 페이지에는 아직 표시하지 않는다. 이 PR 은 연결 API + 보안 계약까지다.
 *
 * <p>보안: {@code /preview} 는 {@code /api/wiki/learning-materials/{id}/preview} 와 동일한 방식 —
 * {@code text/html} inline, CSP 에서 script 실행 금지, sandbox iframe 전용, storageKey / 원본 URL /
 * download endpoint 미노출. Python 응답은 body 만 쓰고 응답 헤더는 여기서 새로 구성한다.
 */
@RestController
@RequiredArgsConstructor
public class MidtermWikiSourcePublicController {

  /** {@code WikiLearningMaterialController#preview} 의 HTML CSP 와 동일해야 한다. */
  static final String HTML_PREVIEW_CSP =
      "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; "
          + "frame-ancestors 'self'; base-uri 'none'; form-action 'none'; object-src 'none'; "
          + "script-src 'none'; connect-src 'none'";

  private final MidtermWikiSourceService service;

  @GetMapping("/api/academies/{handle}/midterm-campaign/wiki-sources")
  public ResponseEntity<List<WikiSourceView>> list(@PathVariable String handle) {
    return ResponseEntity.ok(service.listSources(handle).views());
  }


  @GetMapping("/api/academies/{handle}/midterm-campaign/live-updates")
  public ResponseEntity<List<WikiLiveUpdateView>> liveUpdates(@PathVariable String handle) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
        .body(service.listLiveUpdates(handle));
  }

  @GetMapping("/api/academies/{handle}/midterm-campaign/wiki-sources/{sourceId}/preview")
  public ResponseEntity<byte[]> preview(
      @PathVariable String handle, @PathVariable String sourceId) {
    byte[] html = service.previewHtml(handle, sourceId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.html\"")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", HTML_PREVIEW_CSP)
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
        .body(html);
  }
}
