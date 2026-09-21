package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartReviewWarning {

    /** CONFLICT_RISK | UNKNOWN_RISK | MISSING_SOURCE | HISTORICAL | CASE_DERIVED | UNCLEAR_AUTHORITY */
    private String code;
    private String message;
}
