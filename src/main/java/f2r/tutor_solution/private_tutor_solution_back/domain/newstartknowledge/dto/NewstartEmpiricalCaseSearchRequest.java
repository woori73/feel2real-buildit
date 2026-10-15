package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

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
