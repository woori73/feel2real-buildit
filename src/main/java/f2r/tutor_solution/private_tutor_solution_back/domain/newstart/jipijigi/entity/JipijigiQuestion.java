package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 실제 수집된 카페 질문. 본문 확인 실패는 UNREADABLE로 기록 — 추측 금지. */
@Entity
@Table(name = "newstart_jipijigi_questions",
        uniqueConstraints = @UniqueConstraint(name = "uk_jipijigi_question_article", columnNames = {"club_id", "article_id"}),
        indexes = @Index(name = "idx_jipijigi_question_written", columnList = "written_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiQuestion {

    public enum BodyStatus { READABLE, UNREADABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "article_id", nullable = false, length = 20)
    private String articleId;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "board_name", length = 100)
    private String boardName;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "written_at")
    private LocalDateTime writtenAt;

    @Column(name = "body", columnDefinition = "MEDIUMTEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_status", nullable = false, length = 20)
    private BodyStatus bodyStatus;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "comments_json", columnDefinition = "MEDIUMTEXT")
    private String commentsJson;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String questionText() {
        return body != null && !body.isBlank() ? title + "\n" + body : title;
    }
}
