package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeDeltaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /wemade 쟁점·델타 API.
 * SecurityConfig상 permitAll이므로 컨트롤러에서 viewer별로 응답을 축소한다.
 * - issues: GUEST는 ①② 전체 + ③~⑥ 질문 제목만
 * - delta: GUEST는 최대 1건, MEMBER는 최대 3건
 */
@RestController
@RequestMapping("/api/wemade")
@RequiredArgsConstructor
public class WemadeIssueApiController {

    private final WemadeIssueService issueService;
    private final WemadeDeltaService deltaService;

    @GetMapping("/issues")
    public ResponseEntity<Map<String, Object>> issues(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        String viewer = principal == null ? "GUEST" : "MEMBER";
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("viewer", viewer);
        res.put("version", issueService.getVersion());
        res.put("issues", issueService.forViewer(viewer));
        return ResponseEntity.ok(res);
    }

    @GetMapping("/delta")
    public ResponseEntity<Map<String, Object>> delta(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        String viewer = principal == null ? "GUEST" : "MEMBER";
        List<WemadeDeltaService.DeltaItem> all = deltaService.buildDelta();
        // GUEST는 최대 1건
        List<WemadeDeltaService.DeltaItem> items =
                "MEMBER".equals(viewer) ? all : all.stream().limit(1).toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("viewer", viewer);
        res.put("items", items.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("issueNumber", d.issueNumber());
            m.put("issueShortName", d.issueShortName());
            m.put("dateLabel", d.dateLabel());
            m.put("kindLabel", d.kindLabel());
            m.put("title", d.title());
            m.put("summary", d.summary());
            m.put("sourceUrl", d.sourceUrl());
            return m;
        }).toList());
        res.put("hasUpdate", !items.isEmpty());
        return ResponseEntity.ok(res);
    }
}
