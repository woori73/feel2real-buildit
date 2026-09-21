package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartReviewDetailResponse {

    private NewstartKnowledgeItemResponse item;
    /** 승인 전 확인이 필요한 경고. 비어 있으면 즉시 승인 가능. */
    @Builder.Default
    private List<NewstartReviewWarning> warnings = List.of();
    /** 상태 변경 이력 = provenance. */
    @Builder.Default
    private List<NewstartStatusHistoryResponse> history = List.of();
}
