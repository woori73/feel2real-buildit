package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartEmpiricalCaseListResponse {

    private Long totalCount;
    private List<NewstartEmpiricalCaseResponse> items;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartEmpiricalCaseSearchRequest {

    private String articleId;
    private String authorNickname;
    private String finalReductionRate;
    private String householdType;
    private Integer minAge;
    private Integer maxAge;
    private Long minDebtTotal;
    private Long maxDebtTotal;
    private Long minIncome2025;
    private Long maxIncome2025;
    private Integer minAgeFilter;
    private Integer maxAgeFilter;
    private String householdTypeFilter;
    private String hasFinalReductionRate;
    private Integer page;
    private Integer size;
    private String sort;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartEmpiricalCaseStatsResponse {

    private Long totalCases;
    private Long casesWithActualRate;
    private Long casesWithPredictedRateOnly;
    private Long casesWithAuthorFollowup;
    private Long predictionVsActualMatches;
    private Double avgPredictedRate;
    private Double avgFinalRate;
    private Long totalComments;
    private Long totalReplies;
}