package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemResponse {

    private Long knowledgeId;
    private String sourceType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String authority;
    private String tags;
    private String sourceUrl;
    private LocalDate sourceDate;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer version;
    private String status;
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartEmpiricalCaseResponse {

    private Long caseId;
    private String articleId;
    private String boardName;
    private String title;
    private String authorNickname;
    private String authorGrade;
    private LocalDateTime writtenAt;
    private Integer viewCount;

    private Long debtTotal;
    private String debtBreakdown;
    private Long debtSecured;
    private Long debtUnsecured;
    private Long debtAcquiredByFund;

    private Long assetProperty;
    private Long assetVehicle;
    private Long assetFinancial;

    private String householdType;
    private Integer dependents;
    private Integer age;
    private Long income2025;
    private Long incomeCurrent;
    private String employmentStatus;
    private String businessStatus;
    private String applicationType;

    private String predictedReductionRate;
    private String finalReductionRate;
    private String tempReductionRate;
    private Long predictedRepaymentAmount;
    private Long finalRepaymentAmount;
    private Integer repaymentPeriodMonths;
    private Integer gracePeriodMonths;
    private Long monthlyPayment;

    private String contentRaw;
    private String managerPrediction;
    private String authorFollowupResult;
    private String predictionVsActual;
    private String specialCircumstances;
    private String keyInsights;
    private String sourceUrl;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemCreateRequest {

    private String sourceType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String authority;
    private String tags;
    private String sourceUrl;
    private String sourceDate;
    private String effectiveFrom;
    private String effectiveTo;
    private String authority;
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemUpdateRequest {

    private String sourceType;
    private String category;
    private String title;
    private String summary;
    private String content;
    private String authority;
    private String tags;
    private String sourceUrl;
    private String sourceDate;
    private String effectiveFrom;
    private String effectiveTo;
    private Integer version;
    private String status;
    private String confidence;
    private String relatedCaseIds;
    private String relatedRuleIds;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItemListResponse {

    private Long totalCount;
    private List<NewstartKnowledgeItemResponse> items;
}