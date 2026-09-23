package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 조사 추적 — 질문/초안별 STEP·근거 분류 원장.
 * "Agent가 실제 NewStart DB를 보고 답했는지" 감사 가능해야 한다.
 */
@Entity
@Table(name = "newstart_jipijigi_research_traces",
        indexes = {
                @Index(name = "idx_jipijigi_trace_draft", columnList = "draft_id, step_no"),
                @Index(name = "idx_jipijigi_trace_question", columnList = "question_id, step_no")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiResearchTrace {

    /** 근거 분류. 커뮤니티 사례를 공식 규정처럼 쓰지 않기 위한 필수 구분. */
    public enum SourceClass {
        OFFICIAL_RULE,        // 공식기관 기준
        INTERNAL_CASE,        // NewStart DB 실제 사례
        CCRS_CAFE_CASE,       // 신복위 카페 사례
        BORROWER_CAFE_CASE,   // 부실차주 카페 실제 사례
        OPERATOR_MATERIAL,    // 전자책/운영자 자료
        OTHER
    }

    public enum RefType { KNOWLEDGE_ITEM, EMPIRICAL_CASE, OFFICIAL_UPDATE, CAFE_ARTICLE, EXTERNAL_URL, NONE }

    /** 강제 조사 순서. */
    public static final int STEP_INTERNAL = 1;
    public static final int STEP_CCRS = 2;
    public static final int STEP_BORROWER = 3;
    public static final int STEP_OFFICIAL = 4;
    public static final int STEP_EXTRA_AGENCIES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trace_id")
    private Long traceId;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "command_id")
    private Long commandId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_class", nullable = false, length = 30)
    private SourceClass sourceClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 30)
    private RefType refType;

    @Column(name = "ref_id", length = 50)
    private String refId;

    @Column(name = "ref_url", length = 500)
    private String refUrl;

    @Column(name = "excerpt", columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "searched_at")
    private LocalDateTime searchedAt;
}
