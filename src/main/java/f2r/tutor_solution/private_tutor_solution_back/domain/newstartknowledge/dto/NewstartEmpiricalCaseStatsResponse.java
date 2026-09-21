package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

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
