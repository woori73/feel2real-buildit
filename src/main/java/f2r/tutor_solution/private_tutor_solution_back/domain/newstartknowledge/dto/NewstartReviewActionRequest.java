package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartReviewActionRequest {

    private String reason;
    /** warnings가 있을 때 ACTIVE 승인에 필요. 경고 내용을 확인했다는 명시적 동의. */
    @Builder.Default
    private Boolean confirmed = Boolean.FALSE;
}
