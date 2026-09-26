package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

/** 운영자가 게시 원문을 직접 수정. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiDraftEditRequest {
    private String title;
    private String body;
    private Long targetBoardId;
}
