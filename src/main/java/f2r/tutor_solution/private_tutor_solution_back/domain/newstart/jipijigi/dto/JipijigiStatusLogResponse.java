package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiStatusLogResponse {
    private Long logId;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String reason;
    private LocalDateTime createdAt;
}
