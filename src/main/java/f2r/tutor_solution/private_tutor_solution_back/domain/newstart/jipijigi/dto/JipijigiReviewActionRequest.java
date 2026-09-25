package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

/** 승인/보류/반려 공통 입력. 반려·보류 사유와 승인 대상 게시판 board_row_id를 담는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiReviewActionRequest {
    private String reason;
    private Long targetBoardId;
}
