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
import java.util.List;
import java.util.Optional;

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
        if (req.getStatus() != null) item.setStatus(NewstartKnowledgeItem.KnowledgeStatus.valueOf(req.getStatus()));
        if (req.getConfidence() != null) item.setConfidence(req.getConfidence());
        if (req.getRelatedCaseIds() != null) item.setRelatedCaseIds(req.getRelatedCaseIds());
        if (req.getRelatedRuleIds() != null) item.setRelatedRuleIds(req.getRelatedRuleIds());
        item.setUpdatedBy(actor);

        return toResponse(knowledgeRepository.save(item));
    }

    @Transactional
    public void changeStatus(Long id, String newStatus, String actor, String reason) {
        NewstartKnowledgeItem item = knowledgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge not found: " + id));

        String oldStatus = item.getStatus().name();
        item.setStatus(NewstartKnowledgeItem.KnowledgeStatus.valueOf(newStatus));
        item.setUpdatedBy(actor);
        knowledgeRepository.save(item);

        // Log status change
        NewstartKnowledgeStatusLog log = NewstartKnowledgeStatusLog.builder()
                .knowledgeId(id)
                .fromStatus(oldStatus)
                .toStatus(newStatus)
                .changedBy(actor)
                .changeReason(reason)
                .build();
        statusLogRepository.save(log);
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