package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeGapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 위메이드 가격괴리(SPA 9,200억 vs 시총) 공개 API.
 * 읽기 전용. 자동매매/추천/단일 목표주가 기능 없음.
 * 수급(외국인/기관) 데이터는 미확보이므로 관련 값은 PENDING으로만 반환한다.
 */
@RestController
@RequestMapping("/api/wemade/gap")
@RequiredArgsConstructor
public class WemadeGapController {

    private final WemadeGapService gapService;

    /** 괴리 요인 목록 (경쟁가설 ①~⑥ 매핑). */
    @GetMapping("/factors")
    public ResponseEntity<List<Map<String, Object>>> factors() {
        return ResponseEntity.ok(gapService.factors());
    }

    /** 2026-06-29 이후 가격 이벤트 타임라인. */
    @GetMapping("/timeline")
    public ResponseEntity<List<Map<String, Object>>> timeline() {
        return ResponseEntity.ok(gapService.timeline());
    }

    /**
     * 괴리 헤드라인(2.34조 vs 시총·할인율) + 6/29 이후 가격 패턴 분석.
     * 3단계 판정(FACT/ANALYSIS-PENDING/HYPOTHESIS-미확인) 포함.
     */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> comparison() {
        return ResponseEntity.ok(gapService.comparison());
    }
}
