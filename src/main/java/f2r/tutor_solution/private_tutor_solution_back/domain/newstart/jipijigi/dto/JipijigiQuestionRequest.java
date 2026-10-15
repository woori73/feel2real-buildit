package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** 실제 수집된 카페 질문 1건 입력. 수집 실패 항목은 bodyStatus=UNREADABLE로 보내고 추측 보정을 하지 않는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiQuestionRequest {

    private String articleId;
    private Long clubId;
    private Long menuId;
    private String boardName;
    private String title;
    private String url;
    private LocalDateTime writtenAt;
    private String body;
    /** READABLE | UNREADABLE */
    private String bodyStatus;
    private Integer commentCount;
    private String commentsJson;
    /** 닉네임 재노출 방지: 저장 전 마스킹할 이름 목록(작성자+댓글 작성자). */
    private List<String> nicknames;
}
