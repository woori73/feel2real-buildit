package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeCatalystService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 위메이드 2026 Q4 Triple Catalyst 공개 API.
 * 읽기 전용. 자동매매/추천/단일 목표주가 기능 없음.
 * 확정되지 않은 사건은 status/미확정 딱지로 구분한다.
 */
@RestController
@RequestMapping("/api/wemade/catalyst")
@RequiredArgsConstructor
public class WemadeCatalystController {

    private final WemadeCatalystService catalystService;

    /** 3대 이벤트 카드 (D-Day 자동 계산). */
    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> events() {
        return ResponseEntity.ok(catalystService.catalysts());
    }

    /** 디지털자산기본법 입법 추적. */
    @GetMapping("/policy")
    public ResponseEntity<List<Map<String, Object>>> policy() {
        return ResponseEntity.ok(catalystService.policy());
    }

    /** 복합 시나리오: 2026-01 → 현재 → Q4 타임라인 + 수급 PENDING. */
    @GetMapping("/composite")
    public ResponseEntity<Map<String, Object>> composite() {
        return ResponseEntity.ok(catalystService.composite());
    }
}
