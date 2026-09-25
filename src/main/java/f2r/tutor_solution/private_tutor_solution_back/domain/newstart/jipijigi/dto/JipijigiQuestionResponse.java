package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiQuestionResponse {
    private Long questionId;
    private String articleId;
    private Long clubId;
    private Long menuId;
    private String boardName;
    private String title;
    private String url;
    private LocalDateTime writtenAt;
    private String body;
    private String bodyStatus;
    private Integer commentCount;
    private LocalDateTime collectedAt;
}
