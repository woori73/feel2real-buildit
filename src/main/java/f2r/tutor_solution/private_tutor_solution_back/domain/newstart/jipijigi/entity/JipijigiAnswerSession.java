package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 답변 세션 축적 원장 — 질문 1건에 대한 최종 답변과 그 근거를 한 행에 남긴다.
 *
 * <p>저장 시점은 "답변 생성 성공"이다. 게시 성공 여부와 무관하게 저장하며,
 * 운영자 승인(APPROVED) 이후에만 review_state=ACCEPTED가 되어 다음 질문의 RAG 검색 대상이 된다.
 * Agent 답변 자체는 어떤 경우에도 공식 사실로 승격되지 않는다(재사용 시 INTERNAL_CASE).
 */
@Entity
@Table(name = "newstart_jipijigi_answer_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_jipijigi_session_fp", columnNames = "fingerprint"),
                @UniqueConstraint(name = "uk_jipijigi_session_draft", columnNames = "draft_id")
        },
        indexes = {
                @Index(name = "idx_jipijigi_session_review", columnList = "review_state, answered_at"),
                @Index(name = "idx_jipijigi_session_question", columnList = "question_id"),
                @Index(name = "idx_jipijigi_session_article", columnList = "source_club_id, source_article_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiAnswerSession {

    /** ACCEPTED(운영자 승인)된 세션만 다음 답변의 STEP1 RAG 검색에 포함된다. */
    public enum ReviewState { CANDIDATE, ACCEPTED, REJECTED }

    /** 실제 노출 형태 — V1은 운영자 수동 게시이므로 보고 기준으로만 갱신한다. */
    public enum PublishedAs { NONE, POST, COMMENT }

    public enum Confidence { HIGH, MEDIUM, LOW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    /** 정규화 질문 + 근거 ref 세트 해시. 동일 질문/동일 근거 중복 저장 방지 키. */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "command_id")
    private Long commandId;

    @Column(name = "source_club_id")
    private Long sourceClubId;

    @Column(name = "source_article_id", length = 20)
    private String sourceArticleId;

    @Column(name = "source_comment_id", length = 30)
    private String sourceCommentId;

    /** 이후 회원 후속 댓글·실제 결과와 연결할 식별자. */
    @Column(name = "follow_up_ref", length = 100)
    private String followUpRef;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(name = "question_topic", nullable = false, length = 100)
    private String questionTopic;

    @Column(name = "question_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String questionText;

    @Column(name = "answer_title", nullable = false, length = 300)
    private String answerTitle;

    @Column(name = "answer_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String answerBody;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    @Column(name = "evidence_internal", columnDefinition = "TEXT")
    private String evidenceInternal;

    @Column(name = "evidence_ccrs", columnDefinition = "TEXT")
    private String evidenceCcrs;

    @Column(name = "evidence_borrower", columnDefinition = "TEXT")
    private String evidenceBorrower;

    @Column(name = "evidence_official", columnDefinition = "TEXT")
    private String evidenceOfficial;

    @Column(name = "db_hits", columnDefinition = "TEXT")
    private String dbHits;

    @Column(name = "external_searched", nullable = false)
    private boolean externalSearched;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private Confidence confidence;

    @Column(name = "operator_edited", nullable = false)
    private boolean operatorEdited;

    @Enumerated(EnumType.STRING)
    @Column(name = "published_as", nullable = false, length = 20)
    private PublishedAs publishedAs;

    @Column(name = "published_url", length = 500)
    private String publishedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false, length = 20)
    private ReviewState reviewState;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
