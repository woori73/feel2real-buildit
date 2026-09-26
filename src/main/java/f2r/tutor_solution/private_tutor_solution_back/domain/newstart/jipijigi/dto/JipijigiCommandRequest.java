package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 운영자 수동 명령. commandType 비우면 명령 문장에서 프리셋을 판정한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiCommandRequest {

    @NotBlank
    private String instruction;

    /** RECENT_QUESTIONS | CCRS_RESEARCH | INTRO_POST | CUSTOM */
    private String commandType;
}
