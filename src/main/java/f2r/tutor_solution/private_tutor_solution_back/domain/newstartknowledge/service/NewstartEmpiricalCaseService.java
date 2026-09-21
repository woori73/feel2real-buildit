package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartEmpiricalCaseDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartEmpiricalCaseResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartEmpiricalCaseDtos.NewstartEmpiricalCaseSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewstartEmpiricalCaseService {

    private final NewstartEmpiricalCaseRepository caseRepository;

    public NewstartEmpiricalCaseListResponse search(NewstartEmpiricalCaseSearchRequest req) {
        Pageable pageable = buildPageable(req);
        Specification<NewstartEmpiricalCase> spec = buildSpec(req);
        Page<NewstartEmpiricalCase> page = caseRepository.findAll(spec, pageable);

        return NewstartEmpiricalCaseListResponse.builder()
                .totalCount(page.getTotalElements())
                .items(page.getContent().stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    public List<NewstartEmpiricalCaseResponse> findWithActualRate() {
        return caseRepository.findWithActualReductionRate().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NewstartEmpiricalCaseResponse> findByHouseholdType(String type) {
        return caseRepository.findByHouseholdType(type).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<NewstartEmpiricalCaseResponse> findByArticleId(String articleId) {
        return caseRepository.findByArticleId(articleId).map(this::toResponse);
    }

    public StatsResponse getStats() {
        long total = caseRepository.count();
        long withActual = caseRepository.countByFinalReductionRateIsNotNull();
        long withPrediction = caseRepository.countByPredictedReductionRateIsNotNull();
        long withFollowup = caseRepository.countByAuthorFollowupResultIsNotNull();

        return StatsResponse.builder()
                .totalCases(caseRepository.count())
                .casesWithActualRate(withActual)
                .casesWithPredictedRateOnly(withPrediction)
                .casesWithAuthorFollowup(withFollowup)
                .build();
    }

    private Pageable buildPageable(NewstartEmpiricalCaseSearchRequest req) {
        int page = Math.max(0, req.getPage() != null ? req.getPage() : 0);
        int size = Math.min(100, Math.max(1, req.getSize() != null ? req.getSize() : 20));
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if ("oldest".equals(req.getSort())) sort = Sort.by(Sort.Direction.ASC, "createdAt");
        if ("debt".equals(req.getSort())) sort = Sort.by(Sort.Direction.ASC, "debtTotal");
        if ("income".equals(req.getSort())) sort = Sort.by(Sort.Direction.ASC, "income2025");
        if ("age".equals(req.getSort())) sort = Sort.by(Sort.Direction.ASC, "age");
        return PageRequest.of(page, size, sort);
    }

    private Specification<NewstartEmpiricalCase> buildSpec(NewstartEmpiricalCaseSearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (req.getArticleId() != null && !req.getArticleId().isBlank()) {
                predicates.add(cb.equal(root.get("articleId"), req.getArticleId()));
            }
            if (req.getAuthorNickname() != null && !req.getAuthorNickname().isBlank()) {
                predicates.add(cb.like(root.get("authorNickname"), "%" + req.getAuthorNickname() + "%"));
            }
            if (req.getFinalReductionRate() != null && !req.getFinalReductionRate().isBlank()) {
                predicates.add(cb.equal(root.get("finalReductionRate"), req.getFinalReductionRate()));
            }
            if (req.getHouseholdType() != null && !req.getHouseholdType().isBlank()) {
                predicates.add(cb.equal(root.get("householdType"), req.getHouseholdType()));
            }
            if (req.getMinAge() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("age"), req.getMinAge()));
            }
            if (req.getMaxAge() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("age"), req.getMaxAge()));
            }
            if (req.getMinDebtTotal() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("debtTotal"), req.getMinDebtTotal()));
            }
            if (req.getMaxDebtTotal() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("debtTotal"), req.getMaxDebtTotal()));
            }
            if (req.getMinIncome2025() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("income2025"), req.getMinIncome2025()));
            }
            if (req.getMaxIncome2025() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("income2025"), req.getMaxIncome2025()));
            }
            if ("true".equals(req.getHasFinalReductionRate())) {
                predicates.add(cb.isNotNull(root.get("finalReductionRate")));
            }
            if ("false".equals(req.getHasFinalReductionRate())) {
                predicates.add(cb.isNull(root.get("finalReductionRate")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private NewstartEmpiricalCaseResponse toResponse(NewstartEmpiricalCase c) {
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
}