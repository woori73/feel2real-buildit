package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Agent 게시 초안.
 * 상태: DRAFT → READY_FOR_REVIEW → APPROVED → PUBLISHED / REJECTED.
 * V1에서는 NAVER 연동 미구현으로 PUBLISHED 전이가 서버에서 차단된다.
 */
@Entity
@Table(name = "newstart_jipijigi_drafts",
        indexes = {
                @Index(name = "idx_jipijigi_draft_status", columnList = "status, updated_at"),
                @Index(name = "idx_jipijigi_draft_question", columnList = "question_id"),
                @Index(name = "idx_jipijigi_draft_command", columnList = "command_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiDraft {

    public enum DraftType { INTRO, QUESTION_ANSWER, RESEARCH_REPORT }

    public enum DraftStatus { DRAFT, READY_FOR_REVIEW, APPROVED, PUBLISHED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "command_id")
    private Long commandId;

    @Column(name = "question_id")
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_type", nullable = false, length = 30)
    private DraftType draftType;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "target_board_id")
    private Long targetBoardId;

    @Column(name = "published_url", length = 500)
    private String publishedUrl;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
