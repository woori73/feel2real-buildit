package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/** 게시 allowlist 게시판 등록/수정. menuId는 NAVER에서 실제 확인한 값만 입력(미확인 시 null 유지). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiTargetBoardRequest {

    @NotNull
    private Long clubId;

    private Long menuId;

    @NotNull
    private String boardName;

    private String cafeName;

    private Boolean enabled;

    private String memo;
}
