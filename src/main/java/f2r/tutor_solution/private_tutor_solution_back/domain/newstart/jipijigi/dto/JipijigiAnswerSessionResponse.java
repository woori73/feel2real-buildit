package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiAnswerSessionResponse {
    private Long sessionId;
    private Long draftId;
    private Long questionId;
    private Long commandId;
    private Long sourceClubId;
    private String sourceArticleId;
    private String sourceCommentId;
    private String followUpRef;
    private String questionType;
    private String questionTopic;
    private String questionText;
    private String answerTitle;
    private String answerBody;
    private LocalDateTime answeredAt;
    private String evidenceInternal;
    private String evidenceCcrs;
    private String evidenceBorrower;
    private String evidenceOfficial;
    private String dbHits;
    private boolean externalSearched;
    private String confidence;
    private boolean operatorEdited;
    private String publishedAs;
    private String publishedUrl;
    private String reviewState;
}
