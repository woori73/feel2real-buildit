package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsDataLoader;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsVectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 입시상담 지식 관리자 API — ADMIN 전용 ({@code /api/admin/admissions/**}).
 *
 * <p>지피지기의 JipijigiAdminController 패턴을 입시 전용으로 적용.
 * 승인 상태: DRAFT → REVIEW → ACTIVE.
 *
 * <p><b>APPROVED_CANDIDATE를 ACTIVE로 임의 변경 금지.</b>
 * REVIEW → ACTIVE 전환은 이 컨트롤러의 승인 API를 통해서만 가능하다.
 * 자동 승격 없음.
 */
@RestController
@RequestMapping("/api/admin/admissions")
public class AdmissionsAdminController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionsAdminController.class);

    private final AdmissionsKnowledgeItemRepository knowledgeRepository;
    private final AdmissionsVectorIndex vectorIndex;
    private final AdmissionsDataLoader dataLoader;

    public AdmissionsAdminController(AdmissionsKnowledgeItemRepository knowledgeRepository,
                                     AdmissionsVectorIndex vectorIndex,
                                     AdmissionsDataLoader dataLoader) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorIndex = vectorIndex;
        this.dataLoader = dataLoader;
    }

    /** 지식 목록 (상태별 필터). */
    @GetMapping("/knowledge")
    public ResponseEntity<Page<KnowledgeDto>> listKnowledge(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Page<AdmissionsKnowledgeItem> result;
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            try {
                var st = AdmissionsKnowledgeItem.KnowledgeStatus.valueOf(status.toUpperCase());
                result = knowledgeRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("status"), st), pageable);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            result = knowledgeRepository.findAll(pageable);
        }
        return ResponseEntity.ok(result.map(this::toDto));
    }

    /** 지식 상세. */
    @GetMapping("/knowledge/{id}")
    public ResponseEntity<KnowledgeDto> getKnowledge(@PathVariable Long id) {
        return knowledgeRepository.findById(id)
                .map(k -> ResponseEntity.ok(toDto(k)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * REVIEW → ACTIVE 승인.
     * DRAFT → REVIEW 전환도 이 API로 처리 (action=review/approve).
     * ACTIVE → ARCHIVED도 가능.
     */
    @PostMapping("/knowledge/{id}/review")
    public ResponseEntity<KnowledgeDto> reviewKnowledge(
            @PathVariable Long id,
            @RequestBody ReviewActionRequest request) {
        var item = knowledgeRepository.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        String action = request == null ? "" : request.action();
        var current = item.getStatus();
        var next = switch (action.toLowerCase()) {
            case "review" -> {
                if (current != AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT) {
                    yield null;
                }
                yield AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW;
            }
            case "approve" -> {
                // REVIEW에서만 ACTIVE로 전환 가능. DRAFT에서 직접 ACTIVE 불가.
                if (current != AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW) {
                    yield null;
                }
                yield AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE;
            }
            case "archive" -> AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED;
            case "reject" -> AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT;
            default -> null;
        };
        if (next == null) {
            return ResponseEntity.badRequest().build();
        }
        item.setStatus(next);
        if (request.note() != null) {
            item.setVerificationNote(request.note());
        }
        item.setVersion(item.getVersion() + 1);
        var saved = knowledgeRepository.save(item);
        log.info("admissions knowledge {} status {} -> {} by admin", id, current, next);
        return ResponseEntity.ok(toDto(saved));
    }

    /** 벡터 인덱스 재로드. */
    @PostMapping("/index/reload")
    public ResponseEntity<Map<String, Object>> reloadIndex() {
        vectorIndex.reload();
        return ResponseEntity.ok(Map.of("size", vectorIndex.size(), "ready", vectorIndex.isReady()));
    }

    /**
     * Qoder 시드 JSON 적재 (개발·검증 환경용).
     * 적재된 모든 자료는 DRAFT 상태로 시작한다. 운영 DB 적재 금지.
     */
    @PostMapping("/load/seed")
    public ResponseEntity<Map<String, Object>> loadSeed(@RequestBody Map<String, String> body) {
        String seedJson = body == null ? null : body.get("seedJson");
        if (seedJson == null || seedJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "seedJson required"));
        }
        try {
            var result = dataLoader.loadSeed(seedJson);
            log.info("admissions seed loaded by admin: {}", result);
            return ResponseEntity.ok(Map.of(
                    "universities", result.universities(),
                    "materials", result.materials(),
                    "departments", result.departments(),
                    "types", result.types(),
                    "schedules", result.schedules()));
        } catch (Exception e) {
            log.warn("admissions seed load failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "load failed"));
        }
    }

    /**
     * 상담 지식베이스 적재 (개발·검증 환경용). 전부 DRAFT 상태로 시작.
     */
    @PostMapping("/load/knowledge-base")
    public ResponseEntity<Map<String, Object>> loadKnowledgeBase(@RequestBody Map<String, String> body) {
        String kbJson = body == null ? null : body.get("knowledgeBaseJson");
        if (kbJson == null || kbJson.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "knowledgeBaseJson required"));
        }
        try {
            int count = dataLoader.loadKnowledgeBase(kbJson);
            return ResponseEntity.ok(Map.of("loaded", count, "status", "DRAFT"));
        } catch (Exception e) {
            log.warn("admissions kb load failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "load failed"));
        }
    }

    /** 통계. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long total = knowledgeRepository.count();
        long active = knowledgeRepository.count(
                (root, query, cb) -> cb.equal(root.get("status"),
                        AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE));
        long review = knowledgeRepository.count(
                (root, query, cb) -> cb.equal(root.get("status"),
                        AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW));
        long draft = knowledgeRepository.count(
                (root, query, cb) -> cb.equal(root.get("status"),
                        AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT));
        return ResponseEntity.ok(Map.of(
                "total", total,
                "active", active,
                "review", review,
                "draft", draft,
                "vectorIndexSize", vectorIndex.size(),
                "vectorIndexReady", vectorIndex.isReady()));
    }

    private KnowledgeDto toDto(AdmissionsKnowledgeItem k) {
        return new KnowledgeDto(
                k.getKnowledgeId(),
                k.getAcademicYear(),
                k.getYearConfirmed(),
                k.getTitle(),
                k.getCategory(),
                k.getSourceClass() != null ? k.getSourceClass().name() : null,
                k.getStatus() != null ? k.getStatus().name() : null,
                k.getSourceUrl(),
                k.getSource() != null ? k.getSource().getTitle() : null,
                k.getVerificationNote(),
                k.getVersion(),
                k.getCreatedAt() != null ? k.getCreatedAt().toString() : null);
    }

    public record KnowledgeDto(Long id, Integer academicYear, Boolean yearConfirmed,
                               String title, String category, String sourceClass,
                               String status, String sourceUrl, String sourceTitle,
                               String verificationNote, Integer version, String createdAt) {}

    /** @param action review | approve | archive | reject. @param note 검증 메모. */
    public record ReviewActionRequest(String action, String note) {}
}
