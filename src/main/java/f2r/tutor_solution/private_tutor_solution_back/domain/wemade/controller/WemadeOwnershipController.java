package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wemade.service.WemadeOwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 위메이드 새 대주주(지배구조) 공개 API.
 * 읽기 전용. 자동매매/추천/목표주가 기능 없음.
 */
@RestController
@RequestMapping("/api/wemade/ownership")
@RequiredArgsConstructor
public class WemadeOwnershipController {

    private final WemadeOwnershipService ownershipService;

    /** 지배구조 트리: 노드/간선/간접 경제지분/자금조달/등급 범례 (deterministic). */
    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> tree() {
        return ResponseEntity.ok(ownershipService.tree());
    }

    /** 핵심 인물 프로필 (사실/미확인 분리). */
    @GetMapping("/persons")
    public ResponseEntity<List<Map<String, Object>>> persons() {
        return ResponseEntity.ok(ownershipService.persons());
    }
}
