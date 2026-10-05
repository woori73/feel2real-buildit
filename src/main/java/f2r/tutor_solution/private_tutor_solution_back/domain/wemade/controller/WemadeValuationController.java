package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 위메이드 가치 재평가(SOTP) 공개 API.
 * 읽기 전용. 자동매매/추천/목표주가 기능 없음.
 */
@RestController
@RequestMapping("/api/wemade/valuation")
@RequiredArgsConstructor
public class WemadeValuationController {

    private final WemadeValuationService valuationService;

    @GetMapping("/components")
    public ResponseEntity<List<Map<String, Object>>> components() {
        return ResponseEntity.ok(valuationService.components());
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, Object>>> scenarios() {
        return ResponseEntity.ok(valuationService.scenarios());
    }

    /**
     * 시나리오별 SOTP 부분합. scenario=CONSERVATIVE|BASE|OPTIMISTIC (기본 BASE).
     * 단일 목표주가를 반환하지 않는다.
     */
    @GetMapping("/sotp")
    public ResponseEntity<Map<String, Object>> sotp(
            @RequestParam(defaultValue = "BASE") String scenario) {
        return ResponseEntity.ok(valuationService.sotp(scenario));
    }
}
