package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiResearchTraceResponse {
    private Long traceId;
    private Integer stepNo;
    private String sourceClass;
    private String refType;
    private String refId;
    private String refUrl;
    private String excerpt;
    private String note;
    private LocalDateTime searchedAt;
}
