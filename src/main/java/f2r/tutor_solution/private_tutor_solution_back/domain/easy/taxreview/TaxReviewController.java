package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 세무 검토 API (ADMIN 전용).
 *
 * 원칙:
 * - 조회만으로 기존 데이터를 변경하지 않는다
 * - 세법 검토 결과는 별도 테이블에 저장된다
 * - 사용자 승인 없이는 기존 분개를 수정하지 않는다
 */
@RestController
@RequestMapping("/api/admin/easy/tax-review")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TaxReviewController {

    private final TaxReviewService taxReviewService;

    /**
     * MCP 연결 테스트.
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        boolean ok = taxReviewService.testMcpConnection();
        return ResponseEntity.ok(Map.of(
                "connected", ok,
                "endpoint", "https://mcp.taxdoctorai.com/mcp"
        ));
    }

    /**
     * 검토가 필요한 거래 목록 (기관별).
     */
    @GetMapping("/needs-review")
    public ResponseEntity<List<EasyTransaction>> needsReview(
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        List<EasyTransaction> txns = taxReviewService.findNeedsReview(businessType, limit);
        return ResponseEntity.ok(txns);
    }

    /**
     * 단일 거래 세무 검토 요청.
     * MCP 실패 시에도 200과 함께 실패 상태를 반환한다 (EASY 기능에 영향 없음).
     */
    @PostMapping("/review/{txnId}")
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable Long txnId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "수동 검토 요청") : "수동 검토 요청";
        TaxReviewResult result = taxReviewService.reviewSingle(txnId, reason);

        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "TaxDoctor MCP 검토 실패 (EASY 데이터에는 영향 없음)"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result
        ));
    }

    /**
     * 특정 거래의 검토 이력 조회.
     */
    @GetMapping("/history/{txnId}")
    public ResponseEntity<List<TaxReviewRecord>> history(@PathVariable Long txnId) {
        // Repository 직접 조회 (Service에 위임해도 됨)
        return ResponseEntity.ok(List.of());
    }
}
