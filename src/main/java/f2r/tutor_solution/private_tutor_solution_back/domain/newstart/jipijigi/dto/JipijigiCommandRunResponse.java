package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.util.List;

/** 명령 실행 결과: 명령 1건이 만들어낸 초안 목록. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiCommandRunResponse {
    private JipijigiCommandResponse command;
    private List<JipijigiDraftResponse> drafts;
}
