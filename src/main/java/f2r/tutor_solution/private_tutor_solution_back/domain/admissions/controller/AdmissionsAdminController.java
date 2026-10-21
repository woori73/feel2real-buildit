package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsActivationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsDataLoader;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsEmbeddingService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service.AdmissionsKnowledgeBuildService;
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
    private final f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository materials;
    private final AdmissionsActivationService activationService;
    private final AdmissionsKnowledgeBuildService knowledgeBuildService;
    private final AdmissionsEmbeddingService embeddingService;

    public AdmissionsAdminController(AdmissionsKnowledgeItemRepository knowledgeRepository,
                                     AdmissionsVectorIndex vectorIndex,
                                     AdmissionsDataLoader dataLoader,
                                     f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository materials,
                                     AdmissionsActivationService activationService,
                                     AdmissionsKnowledgeBuildService knowledgeBuildService,
                                     AdmissionsEmbeddingService embeddingService) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorIndex = vectorIndex;
        this.dataLoader = dataLoader;
        this.materials = materials;
        this.activationService = activationService;
        this.knowledgeBuildService = knowledgeBuildService;
        this.embeddingService = embeddingService;
    }

    /** 지식 목록 (상태별 필터). */
    @GetMapping("/knowledge")
    public ResponseEntity<Page<KnowledgeDto>> listKnowledge(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer academicYear) {
        Page<AdmissionsKnowledgeItem> result;
        var pageable = PageRequest.of(Math.max(0,page), Math.max(1,Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            try {
                var st = AdmissionsKnowledgeItem.KnowledgeStatus.valueOf(status.toUpperCase());
                result = knowledgeRepository.findAll(
                        (root, query, cb) -> cb.and(cb.equal(root.get("status"), st), academicYear==null?cb.conjunction():cb.equal(root.get("academicYear"),academicYear)), pageable);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            result = knowledgeRepository.findAll((root, query, cb) -> academicYear==null?cb.conjunction():cb.equal(root.get("academicYear"),academicYear),pageable);
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
        String action = request == null || request.action()==null ? "" : request.action();
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

    /**
     * 한 학년도의 공식자료·학과·전형·일정을 전체 ACTIVE로 승격한다(DRAFT·REVIEW만 대상, ARCHIVED 제외).
     * 확인 문자열 {@code ACTIVATE-ALL-<학년도>}가 필요하다. 실행 관리자와 건수를 로그로 남긴다.
     */
    @PostMapping("/activate-all")
    public ResponseEntity<Map<String, Object>> activateAll(@RequestBody ActivateAllRequest request,
                                                           org.springframework.security.core.Authentication authentication) {
        if (request == null || request.academicYear() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "academicYear required"));
        }
        try {
            var r = activationService.activateAll(request.academicYear(), request.confirm());
            log.info("admissions activate-all by={} year={} materials={} departments={} types={} schedules={} knowledgeItems={} note={}",
                    actorOf(authentication), r.academicYear(), r.materials(),
                    r.departments(), r.types(), r.schedules(), r.knowledgeItems(), request.note());
            return ResponseEntity.ok(Map.of("academicYear", r.academicYear(), "materials", r.materials(),
                    "departments", r.departments(), "types", r.types(), "schedules", r.schedules(),
                    "knowledgeItems", r.knowledgeItems(), "status", "ACTIVATED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "status", "REJECTED"));
        }
    }

    /** 감사 로그용 실행자 표기. 토큰 검증용 값(credentialStamp)은 남기지 않는다. */
    private static String actorOf(org.springframework.security.core.Authentication authentication) {
        if (authentication == null) return "unknown";
        Object principal = authentication.getPrincipal();
        if (principal instanceof f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal p) {
            return p.loginId() + "(userId=" + p.userId() + ")";
        }
        return String.valueOf(authentication.getName());
    }

    public record ActivateAllRequest(Integer academicYear, String confirm, String note) {}

    /** 벡터 인덱스 재로드. */
    @PostMapping("/index/reload")
    public ResponseEntity<Map<String, Object>> reloadIndex() {
        vectorIndex.reload();
        return ResponseEntity.ok(Map.of("size", vectorIndex.size(), "ready", vectorIndex.isReady()));
    }

    /**
     * 구조화 자료(전형·일정·공식자료) 전체를 지식 항목으로 변환한다.
     * 새 항목은 DRAFT로 시작하며 자동 승격은 없다. 본문이 바뀐 항목만 갱신하고 version을 올린다(멱등).
     */
    @PostMapping("/knowledge/build")
    public ResponseEntity<Map<String, Object>> buildKnowledge() {
        var r = knowledgeBuildService.buildAll();
        log.info("admissions knowledge build by admin: created={} updated={} unchanged={}",
                r.created(), r.updated(), r.unchanged());
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("created", r.created());
        out.put("updated", r.updated());
        out.put("unchanged", r.unchanged());
        out.put("changedKnowledgeIds", r.changedKnowledgeIds());
        out.put("status", "BUILT");
        return ResponseEntity.ok(out);
    }

    /**
     * 지식 항목 임베딩 재색인. body: {@code {mode: "MISSING"|"ALL"|"ITEM", knowledgeId: <ITEM일 때 필수>}}.
     * 기본값은 MISSING(ACTIVE 중 임베딩이 없는 항목만). 실패 항목은 사유와 함께 돌려준다.
     */
    @PostMapping("/knowledge/reindex")
    public ResponseEntity<Map<String, Object>> reindexKnowledge(
            @RequestBody(required = false) Map<String, Object> body) {
        String mode = body == null || body.get("mode") == null
                ? "MISSING" : String.valueOf(body.get("mode")).toUpperCase();
        AdmissionsEmbeddingService.ReindexResult r;
        switch (mode) {
            case "ALL" -> r = embeddingService.reindexAll();
            case "ITEM" -> {
                Object idValue = body.get("knowledgeId");
                if (idValue == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "knowledgeId required for ITEM mode", "status", "REJECTED"));
                }
                long id;
                try {
                    id = idValue instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(idValue));
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "knowledgeId must be a number", "status", "REJECTED"));
                }
                r = embeddingService.reindexItem(id);
            }
            default -> r = embeddingService.reindexMissing();
        }
        log.info("admissions knowledge reindex by admin: mode={} requested={} embedded={} skipped={} failed={}",
                mode, r.requested(), r.embedded(), r.skipped(), r.failed());
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("mode", mode);
        out.put("requested", r.requested());
        out.put("embedded", r.embedded());
        out.put("skipped", r.skipped());
        out.put("failed", r.failed());
        out.put("failures", r.failures());
        out.put("vectorIndexSize", vectorIndex.size());
        out.put("status", r.failed() == 0 ? "REINDEXED" : "PARTIAL");
        return ResponseEntity.ok(out);
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
                    "schedules", result.schedules(),
                    "skippedUnknownYear", result.skipped(),
                    "status", result.skipped() > 0 ? "LOADED_WITH_SKIPS" : "LOADED"));
        } catch (AdmissionsDataLoader.SeedLoadException e) {
            // 구조/참조 오류: 전체 롤백되었다. 성공 응답을 내지 않는다.
            log.warn("admissions seed rejected: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage(), "status", "REJECTED"));
        } catch (Exception e) {
            log.warn("admissions seed load failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "load failed", "status", "FAILED"));
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
        } catch (AdmissionsDataLoader.SeedLoadException e) {
            log.warn("admissions kb rejected: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage(), "status", "REJECTED"));
        } catch (Exception e) {
            log.warn("admissions kb load failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "load failed", "status", "FAILED"));
        }
    }

    @GetMapping("/materials")
    public ResponseEntity<Page<MaterialDto>> listMaterials(@RequestParam(defaultValue="0") int page,
            @RequestParam(required=false) Integer academicYear,@RequestParam(required=false) String status) {
        AdmissionsKnowledgeItem.KnowledgeStatus st;
        try { st=status==null || status.isBlank()?null:AdmissionsKnowledgeItem.KnowledgeStatus.valueOf(status); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        var rows=materials.findAll((root,q,cb) -> cb.and(
                academicYear==null?cb.conjunction():cb.equal(root.get("academicYear"),academicYear),
                st==null?cb.conjunction():cb.equal(root.get("status"),st)),PageRequest.of(Math.max(0,page),20));
        return ResponseEntity.ok(rows.map(this::materialDto));
    }
    @PostMapping("/materials/{id}/review")
    public ResponseEntity<MaterialDto> reviewMaterial(@PathVariable Long id,@RequestBody ReviewActionRequest request) {
        var item=materials.findById(id).orElse(null);
        if (item==null) return ResponseEntity.notFound().build();
        String action=request==null || request.action()==null?"":request.action();
        var current=item.getStatus();
        var next=switch(action) {
            case "review" -> current==AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT?AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW:null;
            case "approve" -> current==AdmissionsKnowledgeItem.KnowledgeStatus.REVIEW?AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE:null;
            case "reject" -> AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT;
            case "archive" -> AdmissionsKnowledgeItem.KnowledgeStatus.ARCHIVED;
            default -> null;
        };
        if(next==null) return ResponseEntity.badRequest().build();
        item.setStatus(next);
        return ResponseEntity.ok(materialDto(materials.save(item)));
    }
    private MaterialDto materialDto(f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial m) {
        return new MaterialDto(m.getMaterialId(),m.getAcademicYear(),m.getTitle(),m.getIssuer(),m.getDocumentUrl(),m.getStatus().name());
    }
    public record MaterialDto(Long id,Integer academicYear,String title,String issuer,String documentUrl,String status) {}

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
                k.getCreatedAt() != null ? k.getCreatedAt().toString() : null, k.getContent());
    }

    public record KnowledgeDto(Long id, Integer academicYear, Boolean yearConfirmed,
                               String title, String category, String sourceClass,
                               String status, String sourceUrl, String sourceTitle,
                               String verificationNote, Integer version, String createdAt, String content) {}

    /** @param action review | approve | archive | reject. @param note 검증 메모. */
    public record ReviewActionRequest(String action, String note) {}
}
