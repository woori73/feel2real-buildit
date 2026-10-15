package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiTargetBoardResponse {
    private Long boardRowId;
    private Long clubId;
    private Long menuId;
    private String boardName;
    private String cafeName;
    private boolean enabled;
    /** enabled + menuid NAVER 확인 완료 → 게시 대상 사용 가능. */
    private boolean postable;
    private String memo;
}
