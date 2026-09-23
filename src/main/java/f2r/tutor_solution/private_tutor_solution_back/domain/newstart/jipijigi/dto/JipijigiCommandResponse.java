package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiCommandResponse {
    private Long commandId;
    private String instruction;
    private String commandType;
    private String status;
    private String resultSummary;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
}
