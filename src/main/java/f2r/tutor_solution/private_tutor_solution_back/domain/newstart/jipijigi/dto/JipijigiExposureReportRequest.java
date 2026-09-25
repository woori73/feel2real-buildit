package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto;

import lombok.*;

/**
 * 운영자가 실제 카페에 게시/댓글로 노출한 결과를 Agent에게 보고하는 입력.
 * V1은 NAVER 게시 클라이언트가 없으므로 이 값만 축적 세션에 기록한다 (Agent가 스스로 게시 성공을 주장하지 않는다).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiExposureReportRequest {
    /** POST | COMMENT */
    private String publishedAs;
    private String publishedUrl;
    /** Agent 답변이 댓글로 등록된 경우의 댓글 ID */
    private String commentId;
    /** 이후 회원 후속 댓글·실제 결과 식별자 */
    private String followUpRef;
}
