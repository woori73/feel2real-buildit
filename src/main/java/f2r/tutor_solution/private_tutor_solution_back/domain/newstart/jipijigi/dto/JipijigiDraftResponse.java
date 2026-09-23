package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiDraftResponse {
    private Long draftId;
    private Long commandId;
    private Long questionId;
    private String draftType;
    private String title;
    private String body;
    private String status;
    private Long targetBoardId;
    private String targetBoardName;
    /** V1에서는 항상 null — NAVER 실제 게시 미구현. */
    private String publishedUrl;
    private String rejectReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
