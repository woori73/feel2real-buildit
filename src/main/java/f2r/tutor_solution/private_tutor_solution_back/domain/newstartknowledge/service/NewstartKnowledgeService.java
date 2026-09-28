package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeStatusLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewstartKnowledgeService {

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartKnowledgeRelationRepository relationRepository;
    private final NewstartKnowledgeStatusLogRepository statusLogRepository;

    public Page<NewstartKnowledgeItemResponse> search(
            String q,
            String sourceType,
            String category,
            String status,
            Integer page,
            Integer size,
            String sort) {

        Pageable pageable = buildPageable(page, size, sort);
        Specification<NewstartKnowledgeItem> spec = buildSpec(q, sourceType, category, status);
        Page<NewstartKnowledgeItem> result = knowledgeRepository.findAll(buildSpec(q, sourceType, category, status), buildPageable(page, size, sort));

        return result.map(this::toResponse);
    }

    public List<NewstartKnowledgeItemResponse> findAllActive() {
        return knowledgeRepository.findAllActive().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NewstartKnowledgeItemResponse> findBySourceType(String sourceType) {
        return knowledgeRepository.findBySourceType(NewstartKnowledgeItem.SourceType.valueOf(sourceType)).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NewstartKnowledgeItemResponse> findByCategory(String category) {
        return knowledgeRepository.findByCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<NewstartKnowledgeItemResponse> findById(Long id) {
        return knowledgeRepository.findById(id).map(this::toResponse);
    }

    public List<NewstartKnowledgeItemResponse> findRelatedCases(Long knowledgeId) {
        // TODO: implement relation-based case finding
        return List.of();
    }

    public List<NewstartKnowledgeItemResponse> findRelatedRules(Long knowledgeId) {
        // TODO: implement relation-based rule finding
        return List.of();
    }

    public NewstartKnowledgeStatsResponse getStats() {
        return NewstartKnowledgeStatsResponse.builder()
                .totalKnowledge(knowledgeRepository.count())
                .draftCount(knowledgeRepository.countByStatus(NewstartKnowledgeItem.KnowledgeStatus.DRAFT))
                .reviewCount(knowledgeRepository.countByStatus(NewstartKnowledgeItem.KnowledgeStatus.REVIEW))
                .activeCount(knowledgeRepository.countByStatus(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE))
                .archivedCount(knowledgeRepository.countByStatus(NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED))
                .officialCurrentCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT))
                .officialHistoricalCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.OFFICIAL_HISTORICAL))
                .auditCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.AUDIT))
                .ebookCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.EBOOK))
                .cafeArticleCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.CAFE_ARTICLE))
                .cafeCommentCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.CAFE_COMMENT))
                .caseCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.CASE))
                .operatorKnowledgeCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.OPERATOR_KNOWLEDGE))
                .derivedRuleCount(knowledgeRepository.countBySourceType(NewstartKnowledgeItem.SourceType.DERIVED_RULE))
                .build();
    }

    @Transactional
    public NewstartKnowledgeItemResponse create(NewstartKnowledgeItemCreateRequest req, String actor) {
        NewstartKnowledgeItem item = NewstartKnowledgeItem.builder()
                .sourceType(NewstartKnowledgeItem.SourceType.valueOf(req.getSourceType()))
                .category(req.getCategory())
                .title(req.getTitle())
                .summary(req.getSummary())
                .content(req.getContent())
                .authority(req.getAuthority())
                .tags(req.getTags())
                .sourceUrl(req.getSourceUrl())
                .sourceDate(parseDate(req.getSourceDate()))
                .effectiveFrom(parseDate(req.getEffectiveFrom()))
                .effectiveTo(parseDate(req.getEffectiveTo()))
                .version(1)
                .status(NewstartKnowledgeItem.KnowledgeStatus.DRAFT)
                .confidence(req.getConfidence())
                .relatedCaseIds(req.getRelatedCaseIds())
                .relatedRuleIds(req.getRelatedRuleIds())
                .createdBy(actor)
                .updatedBy(actor)
                .build();

        return toResponse(knowledgeRepository.save(item));
    }

    @Transactional
    public NewstartKnowledgeItemResponse update(Long id, NewstartKnowledgeItemUpdateRequest req, String actor) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));

        if (req.getSourceType() != null) item.setSourceType(NewstartKnowledgeItem.SourceType.valueOf(req.getSourceType()));
        if (req.getCategory() != null) item.setCategory(req.getCategory());
        if (req.getTitle() != null) item.setTitle(req.getTitle());
        if (req.getSummary() != null) item.setSummary(req.getSummary());
        if (req.getContent() != null) item.setContent(req.getContent());
        if (req.getAuthority() != null) item.setAuthority(req.getAuthority());
        if (req.getTags() != null) item.setTags(req.getTags());
        if (req.getSourceUrl() != null) item.setSourceUrl(req.getSourceUrl());
        if (req.getSourceDate() != null) item.setSourceDate(parseDate(req.getSourceDate()));
        if (req.getEffectiveFrom() != null) item.setEffectiveFrom(parseDate(req.getEffectiveFrom()));
        if (req.getEffectiveTo() != null) item.setEffectiveTo(parseDate(req.getEffectiveTo()));
        if (req.getVersion() != null) item.setVersion(req.getVersion());
        if (req.getStatus() != null) {
            // ACTIVE 전환은 REVIEW 승인 API(approveReview)를 통해서만 가능하다. 일반 수정으로 우회 금지.
            if (NewstartKnowledgeItem.KnowledgeStatus.ACTIVE.name().equals(req.getStatus())
                    && item.getStatus() != NewstartKnowledgeItem.KnowledgeStatus.ACTIVE) {
                throw new IllegalStateException(
                        "ACTIVE 전환은 REVIEW 승인 API를 통해서만 가능하다 (knowledgeId=" + id + ")");
            }
            item.setStatus(NewstartKnowledgeItem.KnowledgeStatus.valueOf(req.getStatus()));
        }
        if (req.getConfidence() != null) item.setConfidence(req.getConfidence());
        if (req.getRelatedCaseIds() != null) item.setRelatedCaseIds(req.getRelatedCaseIds());
        if (req.getRelatedRuleIds() != null) item.setRelatedRuleIds(req.getRelatedRuleIds());
        item.setUpdatedBy(actor);

        return toResponse(knowledgeRepository.save(item));
    }

    // ── ADMIN Review Workflow ──────────────────────────────────────────
    // 원칙: 신규 Knowledge = REVIEW. DRAFT→ACTIVE 직행 금지.
    // Production retrieval = ACTIVE only (NewstartKnowledgeRetrievalService).
    // ACTIVE 전환은 명시적 ADMIN action(approveReview)만 허용. 자동 승인 금지.

    /** 허용된 상태 전이. 여기에 없으면 changeStatus에서 거부한다. */
    private static final Map<NewstartKnowledgeItem.KnowledgeStatus, Set<NewstartKnowledgeItem.KnowledgeStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(Map.of(
                    NewstartKnowledgeItem.KnowledgeStatus.DRAFT,
                            EnumSet.of(NewstartKnowledgeItem.KnowledgeStatus.REVIEW,
                                    NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED),
                    NewstartKnowledgeItem.KnowledgeStatus.REVIEW,
                            EnumSet.of(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE,
                                    NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED,
                                    NewstartKnowledgeItem.KnowledgeStatus.DRAFT),
                    NewstartKnowledgeItem.KnowledgeStatus.ACTIVE,
                            EnumSet.of(NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED),
                    NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED,
                            EnumSet.of(NewstartKnowledgeItem.KnowledgeStatus.DRAFT,
                                    NewstartKnowledgeItem.KnowledgeStatus.REVIEW)));

    @Transactional
    public void changeStatus(Long id, String newStatus, String actor, String reason) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));

        NewstartKnowledgeItem.KnowledgeStatus from = item.getStatus();
        NewstartKnowledgeItem.KnowledgeStatus to;
        try {
            to = NewstartKnowledgeItem.KnowledgeStatus.valueOf(newStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown status: " + newStatus + " (knowledgeId=" + id + ")");
        }
        Set<NewstartKnowledgeItem.KnowledgeStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "허용되지 않은 상태 전이입니다 " + from + " -> " + to + " (knowledgeId=" + id + ")");
        }
        // ACTIVE/ARCHIVED 전환에는 사유가 필수 (provenance 보존).
        if ((to == NewstartKnowledgeItem.KnowledgeStatus.ACTIVE
                        || to == NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED)
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("ACTIVE/ARCHIVED 전환에는 사유가 필수입니다(knowledgeId=" + id + ")");
        }
        // REVIEW→ACTIVE는 경고 확인 게이트를 거친다.
        if (from == NewstartKnowledgeItem.KnowledgeStatus.REVIEW
                && to == NewstartKnowledgeItem.KnowledgeStatus.ACTIVE) {
            List<NewstartReviewWarning> warnings = computeWarnings(item);
            if (!warnings.isEmpty()) {
                throw new ReviewConfirmationRequiredException(id, warnings);
            }
        }

        applyStatus(item, to, actor, reason);
    }

    /** REVIEW 목록 (검토함). */
    public Page<NewstartKnowledgeItemResponse> getReviewQueue(Integer page, Integer size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) ->
                cb.equal(root.get("status"), NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        return knowledgeRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /** REVIEW 상세: 본문 + 경고 + provenance(이력). */
    public NewstartReviewDetailResponse getReviewDetail(Long id) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));
        return NewstartReviewDetailResponse.builder()
                .item(toResponse(item))
                .warnings(computeWarnings(item))
                .history(loadHistory(id))
                .build();
    }

    /** 상태 변경 이력 = provenance 조회. */
    public List<NewstartStatusHistoryResponse> getHistory(Long id) {
        knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));
        return loadHistory(id);
    }

    /**
     * REVIEW → ACTIVE 명시적 승인.
     * 경고가 있는데 confirmed가 아니면 {@link ReviewConfirmationRequiredException} (HTTP 409).
     * OFFICIAL/CASE/EBOOK 모두 동일한 workflow를 탄다. 경고는 차단이 아니라 관리자 확인 대상이다.
     */
    @Transactional
    public NewstartKnowledgeItemResponse approveReview(Long id, String actor, String reason, boolean confirmed) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));
        if (item.getStatus() != NewstartKnowledgeItem.KnowledgeStatus.REVIEW) {
            throw new IllegalStateException(
                    "REVIEW 상태에서만 승인할 수 있습니다 (현재=" + item.getStatus() + ", knowledgeId=" + id + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("승인 사유가 필수입니다(knowledgeId=" + id + ")");
        }
        List<NewstartReviewWarning> warnings = computeWarnings(item);
        if (!warnings.isEmpty() && !confirmed) {
            throw new ReviewConfirmationRequiredException(id, warnings);
        }

        applyStatus(item, NewstartKnowledgeItem.KnowledgeStatus.ACTIVE, actor, reason);
        return toResponse(item);
    }

    /** REVIEW 유지 (보류). 상태는 바꾸지 않고 검토 메모만 이력에 남긴다. */
    @Transactional
    public NewstartKnowledgeItemResponse keepReview(Long id, String actor, String reason) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));
        if (item.getStatus() != NewstartKnowledgeItem.KnowledgeStatus.REVIEW) {
            throw new IllegalStateException(
                    "REVIEW 상태에서만 유지할 수 있습니다 (현재=" + item.getStatus() + ", knowledgeId=" + id + ")");
        }
        NewstartKnowledgeStatusLog log = NewstartKnowledgeStatusLog.builder()
                .knowledgeId(id)
                .fromStatus("REVIEW")
                .toStatus("REVIEW")
                .changedBy(actor)
                .changeReason(reason != null ? reason : "REVIEW 유지")
                .build();
        statusLogRepository.save(log);
        return toResponse(item);
    }

    /** REVIEW → ARCHIVED (반려/보관). ACTIVE → ARCHIVED도 허용. */
    @Transactional
    public NewstartKnowledgeItemResponse archiveReview(Long id, String actor, String reason) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));
        if (item.getStatus() != NewstartKnowledgeItem.KnowledgeStatus.REVIEW
                && item.getStatus() != NewstartKnowledgeItem.KnowledgeStatus.ACTIVE) {
            throw new IllegalStateException(
                    "REVIEW/ACTIVE 상태에서만 보관할 수 있습니다 (현재=" + item.getStatus() + ", knowledgeId=" + id + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("보관 사유가 필수입니다(knowledgeId=" + id + ")");
        }
        applyStatus(item, NewstartKnowledgeItem.KnowledgeStatus.ARCHIVED, actor, reason);
        return toResponse(item);
    }

    private void applyStatus(NewstartKnowledgeItem item,
                             NewstartKnowledgeItem.KnowledgeStatus newStatus,
                             String actor,
                             String reason) {
        String oldStatus = item.getStatus().name();
        item.setStatus(newStatus);
        item.setUpdatedBy(actor);
        knowledgeRepository.save(item);

        NewstartKnowledgeStatusLog log = NewstartKnowledgeStatusLog.builder()
                .knowledgeId(item.getKnowledgeId())
                .fromStatus(oldStatus)
                .toStatus(newStatus.name())
                .changedBy(actor)
                .changeReason(reason)
                .build();
        statusLogRepository.save(log);
    }

    private List<NewstartStatusHistoryResponse> loadHistory(Long id) {
        return statusLogRepository.findByKnowledgeIdOrderByCreatedAtDesc(id).stream()
                .map(l -> NewstartStatusHistoryResponse.builder()
                        .logId(l.getLogId())
                        .knowledgeId(l.getKnowledgeId())
                        .fromStatus(l.getFromStatus())
                        .toStatus(l.getToStatus())
                        .changedBy(l.getChangedBy())
                        .changeReason(l.getChangeReason())
                        .createdAt(l.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 승인 전 사전경고: 스키마 변경 없이 기존 필드에서 파생한다 (운영 migration 금지 준수).
     * WARNING != REJECTION. 특히 CASE/EBOOK은 OFFICIAL이 아니라는 이유만으로 승인 차단하지 않는다.
     * - CONFLICT_RISK: tags/content에 CONFLICT/충돌 표기
     * - UNKNOWN_RISK: tags/content/confidence에 UNKNOWN/미확인/미해결 표기
     * - MISSING_SOURCE: sourceUrl 없음
     * - HISTORICAL: OFFICIAL_HISTORICAL (과거 규정, CURRENT와 혼합 금지)
     * - CASE_DERIVED: CASE 기반 DERIVED_RULE (소수 사례 일반화 금지)
     * - UNCLEAR_AUTHORITY: authority 없음
     *
     * <p>static: 지피지기 RAG 리랭크({@code JipijigiEvidenceScorer})가 같은 규칙을 근거 신뢰도로
     * 재사용한다. 승인 화면과 검색 결과가 서로 다른 위험 판정을 하면 안 된다.
     */
    public static List<NewstartReviewWarning> computeWarnings(NewstartKnowledgeItem item) {
        List<NewstartReviewWarning> warnings = new ArrayList<>();
        String haystack = String.join("\n",
                nullToEmpty(item.getTitle()),
                nullToEmpty(item.getSummary()),
                nullToEmpty(item.getContent()),
                nullToEmpty(item.getTags()),
                nullToEmpty(item.getConfidence()));

        if (haystack.contains("CONFLICT") || haystack.contains("충돌")) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("CONFLICT_RISK")
                    .message("CONFLICT 표기가 있습니다. scope/effective date/적용범위·시점 근거 없이 해결하지 마십시오. "
                            + "예: 10일·31일 충돌은 CONFLICT_REVIEW 유지가 기본입니다.")
                    .build());
        }
        if (haystack.contains("UNKNOWN") || haystack.contains("미확인") || haystack.contains("미해결")) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("UNKNOWN_RISK")
                    .message("UNKNOWN/미확인 표기가 있습니다. 근거가 보강되기까지 ACTIVE는 보류하십시오.")
                    .build());
        }
        if (item.getSourceUrl() == null || item.getSourceUrl().isBlank()) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("MISSING_SOURCE")
                    .message("source URL이 없습니다. 원문 링크 없이 ACTIVE 승인하지 마십시오.")
                    .build());
        }
        if (item.getSourceType() == NewstartKnowledgeItem.SourceType.OFFICIAL_HISTORICAL) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("HISTORICAL")
                    .message("OFFICIAL_HISTORICAL(과거 규정)입니다. 공식 CURRENT와 혼합하지 마십시오. "
                            + "effective date/적용범위를 상세에서 반드시 확인하십시오.")
                    .build());
        }
        if (item.getSourceType() == NewstartKnowledgeItem.SourceType.DERIVED_RULE
                && item.getRelatedCaseIds() != null && !item.getRelatedCaseIds().isBlank()) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("CASE_DERIVED")
                    .message("CASE 기반 DERIVED_RULE입니다. 1~2개 실제 사례만으로 일반화하지 마십시오 "
                            + "(예: '부실차주는 보통 XX% 감면' 금지). 확정 감면율과 예상 감면율을 구분하십시오.")
                    .build());
        }
        if (item.getAuthority() == null || item.getAuthority().isBlank()) {
            warnings.add(NewstartReviewWarning.builder()
                    .code("UNCLEAR_AUTHORITY")
                    .message("authority(발행/작성 주체)가 불명확합니다. 출처 권위를 확인하십시오.")
                    .build());
        }
        return warnings;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 경고가 있는데 확인 없이 승인하려 할 때 발생. Controller에서 HTTP 409 + warnings로 변환한다. */
    public static class ReviewConfirmationRequiredException extends RuntimeException {
        private final Long knowledgeId;
        private final List<NewstartReviewWarning> warnings;

        public ReviewConfirmationRequiredException(Long knowledgeId, List<NewstartReviewWarning> warnings) {
            super("경고 " + warnings.size() + "건 확인이 필요합니다(knowledgeId=" + knowledgeId + ")");
            this.knowledgeId = knowledgeId;
            this.warnings = warnings;
        }

        public Long getKnowledgeId() {
            return knowledgeId;
        }

        public List<NewstartReviewWarning> getWarnings() {
            return warnings;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Pageable buildPageable(Integer page, Integer size, String sort) {
        int p = Math.max(0, page != null ? page : 0);
        int s = Math.min(100, Math.max(1, size != null ? size : 20));
        Sort.Direction dir = Sort.Direction.DESC;
        String prop = "createdAt";
        if ("oldest".equals(sort)) dir = Sort.Direction.ASC;
        if ("title".equals(sort)) prop = "title";
        if ("status".equals(sort)) prop = "status";
        if ("sourceType".equals(sort)) prop = "sourceType";
        return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size != null ? size : 20)), Sort.by(dir, prop));
    }

    private Specification<NewstartKnowledgeItem> buildSpec(String q, String sourceType, String category, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                Predicate titleLike = cb.like(root.get("title"), "%" + q + "%");
                Predicate contentLike = cb.like(root.get("content"), "%" + q + "%");
                predicates.add(cb.or(titleLike, contentLike));
            }
            if (sourceType != null && !sourceType.isBlank()) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private NewstartKnowledgeItemResponse toResponse(NewstartKnowledgeItem item) {
        return NewstartKnowledgeItemResponse.builder()
                .knowledgeId(item.getKnowledgeId())
                .sourceType(item.getSourceType().name())
                .category(item.getCategory())
                .title(item.getTitle())
                .summary(item.getSummary())
                .content(item.getContent())
                .authority(item.getAuthority())
                .tags(item.getTags())
                .sourceUrl(item.getSourceUrl())
                .sourceDate(item.getSourceDate())
                .effectiveFrom(item.getEffectiveFrom())
                .effectiveTo(item.getEffectiveTo())
                .version(item.getVersion())
                .status(item.getStatus().name())
                .confidence(item.getConfidence())
                .relatedCaseIds(item.getRelatedCaseIds())
                .relatedRuleIds(item.getRelatedRuleIds())
                .createdBy(item.getCreatedBy())
                .updatedBy(item.getUpdatedBy())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}