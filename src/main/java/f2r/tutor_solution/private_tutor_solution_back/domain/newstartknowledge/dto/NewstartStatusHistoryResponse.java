package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartStatusHistoryResponse {

    private Long logId;
    private Long knowledgeId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private String changeReason;
    private LocalDateTime createdAt;
}
