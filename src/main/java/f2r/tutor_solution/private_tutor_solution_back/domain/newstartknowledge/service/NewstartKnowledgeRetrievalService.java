package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.*;
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
public class NewstartKnowledgeRetrievalService {

    private final NewstartKnowledgeItemRepository knowledgeRepository;
    private final NewstartEmpiricalCaseRepository empiricalCaseRepository;
    private final NewstartKnowledgeRelationRepository relationRepository;

    /**
     * AI가 회원 질문에서 관련 지식을 검색할 때 사용
     * 질문의 키워드를 기반으로 ACTIVE 지식 중 가장 관련도 높은 것을 반환
     */
    public List<NewstartKnowledgeItemResponse> retrieveForQuestion(String question, int limit) {
        if (question == null || question.isBlank()) return List.of();

        // 키워드 추출 (간단한 토큰화)
        String[] keywords = extractKeywords(question);
        if (keywords.length == 0) return List.of();

        // OR 조건으로 검색
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            List<Predicate> keywordPredicates = new ArrayList<>();
            for (String kw : keywords) {
                if (kw.length() < 2) continue;
                String pattern = "%" + kw + "%";
                Predicate titleLike = cb.like(root.get("title"), "%" + kw + "%");
                Predicate contentLike = cb.like(root.get("content"), "%" + kw + "%");
                Predicate tagLike = cb.like(root.get("tags"), "%" + kw + "%");
                Predicate categoryLike = cb.like(root.get("category"), "%" + kw + "%");
                keywordPredicates.add(cb.or(titleLike, contentLike, tagLike, categoryLike));
            }
            if (!keywordPredicates.isEmpty()) {
                predicates.add(cb.or(keywordPredicates.toArray(new Predicate[0])));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(0, limit != 0 ? limit : 10);
        return knowledgeRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 특정 카테고리의 ACTIVE 지식 조회
     */
    public List<NewstartKnowledgeItemResponse> findActiveByCategory(String category, int limit) {
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(0, limit);
        return knowledgeRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 특정 소스 타입의 ACTIVE 지식 조회
     */
    public List<NewstartKnowledgeItemResponse> findActiveBySourceType(String sourceType, int limit) {
        return knowledgeRepository.findAllActive().stream()
                .filter(k -> k.getSourceType().name().equals(sourceType))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    /**
     * 감면율 카테고리의 핵심 지식 조회 (AI 답변용)
     */
    public List<NewstartKnowledgeItemResponse> getGamianCoreKnowledge(int limit) {
        return findActiveByCategory("감면율", limit);
    }

    /**
     * 현재 시점(effective_from <= today <= effective_to)에서 유효한 지식만 조회
     */
    public List<NewstartKnowledgeItemResponse> findCurrentlyEffective(int limit) {
        LocalDate today = LocalDate.now();
        Specification<NewstartKnowledgeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), today));
            Predicate effectiveTo = cb.or(
                    cb.isNull(root.get("effectiveTo")),
                    cb.greaterThanOrEqualTo(root.get("effectiveTo"), today)
            );
            predicates.add(effectiveTo);
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return knowledgeRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 키워드 기반 검색 + 유효성 검증
     */
    public List<NewstartKnowledgeItemResponse> searchEffectiveKnowledge(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();

        String[] keywords = extractKeywords(query);
        if (keywords.length == 0) return List.of();

        LocalDate today = LocalDate.now();
        Specification<NewstartKnowledgeItem> spec = (root, crit, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), LocalDate.now()));
            predicates.add(cb.or(
                    cb.isNull(root.get("effectiveTo")),
                    cb.greaterThanOrEqualTo(root.get("effectiveTo"), LocalDate.now())
            ));

            List<Predicate> keywordPredicates = new ArrayList<>();
            for (String kw : keywords) {
                if (kw.length() < 2) continue;
                String pattern = "%" + kw + "%";
                Predicate titleLike = cb.like(root.get("title"), "%" + kw + "%");
                Predicate contentLike = cb.like(root.get("content"), "%" + kw + "%");
                Predicate tagLike = cb.like(root.get("tags"), "%" + kw + "%");
                Predicate categoryLike = cb.like(root.get("category"), "%" + kw + "%");
                keywordPredicates.add(cb.or(titleLike, contentLike, tagLike, categoryLike));
            }
            if (!keywordPredicates.isEmpty()) {
                predicates.add(cb.or(keywordPredicates.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return knowledgeRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 관련 실제 사례 조회 (AI 답변 보강용)
     */
    public List<NewstartEmpiricalCaseResponse> findRelatedCases(
            Long debtMin, Long debtMax,
            Long incomeMin, Long incomeMax,
            Integer ageMin, Integer ageMax,
            String householdType,
            int limit) {

        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (debtMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("debtTotal"), debtMin));
            if (debtMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("debtTotal"), debtMax));
            if (incomeMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("income2025"), incomeMin));
            if (incomeMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("income2025"), incomeMax));
            if (ageMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("age"), ageMin));
            if (ageMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("age"), ageMax));
            if (householdType != null && !householdType.isBlank()) {
                predicates.add(cb.equal(root.get("householdType"), householdType));
            }
            predicates.add(cb.isNotNull(root.get("finalReductionRate")));

            query.orderBy(cb.desc(root.get("writtenAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * 실제 감면율이 확인된 사례만 조회
     */
    public List<NewstartEmpiricalCaseResponse> findCasesWithActualRate(int limit) {
        return empiricalCaseRepository.findWithActualReductionRate().stream()
                .limit(limit)
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * 유사 조건의 실제 사례 찾기 (채무/소득/연령/가구형 근사)
     */
    public List<NewstartEmpiricalCaseResponse> findSimilarCases(
            Long debtTotal, Long income2025, Integer age, String householdType,
            int tolerance, int limit) {

        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (debtTotal != null) {
                predicates.add(cb.between(root.get("debtTotal"),
                        debtTotal - (debtTotal * 30 / 100),
                        debtTotal + (debtTotal * 30 / 100)));
            }
            if (income2025 != null) {
                predicates.add(cb.between(root.get("income2025"),
                        income2025 - (income2025 * 30 / 100),
                        income2025 + (income2025 * 30 / 100)));
            }
            if (age != null) {
                predicates.add(cb.between(root.get("age"), age - 5, age + 5));
            }
            if (householdType != null && !householdType.isBlank()) {
                predicates.add(cb.equal(root.get("householdType"), householdType));
            }
            predicates.add(cb.isNotNull(root.get("finalReductionRate")));

            query.orderBy(cb.desc(root.get("writtenAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * 관련 실제 사례 조회 — 결과 확정 여부와 무관하게 전부 조회 (canonical CASE RETRIEVAL).
     *
     * <p>공식자료가 없다는 이유로 관련 CASE를 제거하지 않는다. 확정 감면율이 없는
     * RESULT_PARTIAL/UNRESOLVED/ONGOING 사례도 그대로 반환하며, 호출자가
     * {@link NewstartCanonicalAnswerPolicy#classifyCaseOutcome} 으로 상태를 표시한다.
     * 기존 {@link #findRelatedCases} 는 확정 결과 사례만 필요할 때 그대로 사용한다.
     */
    public List<NewstartEmpiricalCaseResponse> findRelatedCasesRegardlessOfOutcome(
            Long debtMin, Long debtMax,
            Long incomeMin, Long incomeMax,
            Integer ageMin, Integer ageMax,
            String householdType,
            int limit) {

        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (debtMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("debtTotal"), debtMin));
            if (debtMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("debtTotal"), debtMax));
            if (incomeMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("income2025"), incomeMin));
            if (incomeMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("income2025"), incomeMax));
            if (ageMin != null) predicates.add(cb.greaterThanOrEqualTo(root.get("age"), ageMin));
            if (ageMax != null) predicates.add(cb.lessThanOrEqualTo(root.get("age"), ageMax));
            if (householdType != null && !householdType.isBlank()) {
                predicates.add(cb.equal(root.get("householdType"), householdType));
            }
            // 확정 감면율(finalReductionRate) 조건을 두지 않는다 — 미확정 사례도 보존.

            query.orderBy(cb.desc(root.get("writtenAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * 유사 조건의 실제 사례 찾기 — 결과 확정 여부와 무관하게 전부 조회 (canonical CASE RETRIEVAL).
     *
     * <p>기존 {@link #findSimilarCases} 와 조건은 같고 확정 감면율 조건만 없다.
     */
    public List<NewstartEmpiricalCaseResponse> findSimilarCasesRegardlessOfOutcome(
            Long debtTotal, Long income2025, Integer age, String householdType,
            int tolerance, int limit) {

        Specification<NewstartEmpiricalCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (debtTotal != null) {
                predicates.add(cb.between(root.get("debtTotal"),
                        debtTotal - (debtTotal * 30 / 100),
                        debtTotal + (debtTotal * 30 / 100)));
            }
            if (income2025 != null) {
                predicates.add(cb.between(root.get("income2025"),
                        income2025 - (income2025 * 30 / 100),
                        income2025 + (income2025 * 30 / 100)));
            }
            if (age != null) {
                predicates.add(cb.between(root.get("age"), age - 5, age + 5));
            }
            if (householdType != null && !householdType.isBlank()) {
                predicates.add(cb.equal(root.get("householdType"), householdType));
            }
            // 확정 감면율(finalReductionRate) 조건을 두지 않는다 — 미확정 사례도 보존.

            query.orderBy(cb.desc(root.get("writtenAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return empiricalCaseRepository.findAll(spec, PageRequest.of(0, limit)).stream()
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * Knowledge Gap 감지 - 질문에서 Wiki에 없는 정보 발견 시 기록
     */
    @Transactional
    public KnowledgeGapReport detectGap(String question, String aiAnswer, List<Long> usedKnowledgeIds) {
        // 사용된 지식의 카테고리/태그 분석
        // 질문에서 키워드 추출
        // 기존 ACTIVE 지식과 매칭 여부 확인
        // 매칭 안 되면 Gap 리포트 생성

        String[] keywords = extractKeywords(question);
        int matched = 0;
        for (String kw : keywords) {
            if (kw.length() < 2) continue;
            List<NewstartKnowledgeItem> matches = knowledgeRepository
                    .findByStatusAndSourceType(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE,
                            NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT); // 예시
            if (!matches.isEmpty()) matched++;
        }

        return KnowledgeGapReport.builder()
                .question(question)
                .keywords(keywords)
                .matchedKnowledgeCount(matched)
                .gapSeverity(matched == 0 ? "HIGH" : matched < 2 ? "MEDIUM" : "LOW")
                .usedKnowledgeIds(usedKnowledgeIds)
                .aiAnswer(aiAnswer)
                .recommendedAction(matched == 0 ? "NEW_KNOWLEDGE_REQUIRED" : "PARTIAL_COVERAGE")
                .build();
    }

    private String[] extractKeywords(String text) {
        if (text == null) return new String[0];
        // 간단한 토큰화: 2글자 이상 단어 추출
        return java.util.Arrays.stream(text.replaceAll("[^가-힣a-zA-Z0-9]", " ")
                .split("\\s+"))
                .filter(s -> s.length() >= 2)
                .toArray(String[]::new);
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

    private NewstartEmpiricalCaseResponse toCaseResponse(NewstartEmpiricalCase c) {
        return NewstartEmpiricalCaseResponse.builder()
                .caseId(c.getCaseId())
                .articleId(c.getArticleId())
                .boardName(c.getBoardName())
                .title(c.getTitle())
                .authorNickname(c.getAuthorNickname())
                .authorGrade(c.getAuthorGrade())
                .writtenAt(c.getWrittenAt())
                .viewCount(c.getViewCount())
                .debtTotal(c.getDebtTotal())
                .debtBreakdown(c.getDebtBreakdown())
                .debtSecured(c.getDebtSecured())
                .debtUnsecured(c.getDebtUnsecured())
                .debtAcquiredByFund(c.getDebtAcquiredByFund())
                .assetProperty(c.getAssetProperty())
                .assetVehicle(c.getAssetVehicle())
                .assetFinancial(c.getAssetFinancial())
                .householdType(c.getHouseholdType())
                .dependents(c.getDependents())
                .age(c.getAge())
                .income2025(c.getIncome2025())
                .incomeCurrent(c.getIncomeCurrent())
                .employmentStatus(c.getEmploymentStatus())
                .businessStatus(c.getBusinessStatus())
                .applicationType(c.getApplicationType())
                .predictedReductionRate(c.getPredictedReductionRate())
                .finalReductionRate(c.getFinalReductionRate())
                .tempReductionRate(c.getTempReductionRate())
                .predictedRepaymentAmount(c.getPredictedRepaymentAmount())
                .finalRepaymentAmount(c.getFinalRepaymentAmount())
                .repaymentPeriodMonths(c.getRepaymentPeriodMonths())
                .gracePeriodMonths(c.getGracePeriodMonths())
                .monthlyPayment(c.getMonthlyPayment())
                .contentRaw(c.getContentRaw())
                .managerPrediction(c.getManagerPrediction())
                .authorFollowupResult(c.getAuthorFollowupResult())
                .predictionVsActual(c.getPredictionVsActual())
                .specialCircumstances(c.getSpecialCircumstances())
                .keyInsights(c.getKeyInsights())
                .sourceUrl(c.getSourceUrl())
                .collectedAt(c.getCollectedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KnowledgeGapReport {
        private String question;
        private String[] keywords;
        private int matchedKnowledgeCount;
        private String gapSeverity; // HIGH, MEDIUM, LOW
        private List<Long> usedKnowledgeIds;
        private String aiAnswer;
        private String recommendedAction; // NEW_KNOWLEDGE_REQUIRED, PARTIAL_COVERAGE, SUFFICIENT
    }
}