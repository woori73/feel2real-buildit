package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "newstart_knowledge_items",
        indexes = {
                @Index(name = "idx_newstart_knowledge_status_type", columnList = "status, source_type"),
                @Index(name = "idx_newstart_knowledge_category", columnList = "category"),
                @Index(name = "idx_newstart_knowledge_effective", columnList = "effective_from, effective_to"),
                @Index(name = "idx_newstart_knowledge_source_date", columnList = "source_date"),
                @Index(name = "idx_newstart_knowledge_created", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewstartKnowledgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_id")
    private Long knowledgeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private SourceType sourceType;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "authority", length = 50)
    private String authority;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_date")
    private LocalDate sourceDate;

    /**
     * 갈무리 중복 제거 키(V1012, UNIQUE). 같은 출처·같은 항목이 두 번 쌓이면 지피지기 RAG 근거가
     * 중복되어 답변이 한 쪽으로 기운다 — Agent/운영자 저장 경로에서 안정 키(예: source_type+원본 id)를
     * 넣어 충돌시키지 않고 갱신하도록 한다. nullable: 수기 등록 항목은 키 없이도 저장된다.
     */
    @Column(name = "dedup_key", length = 191)
    private String dedupKey;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private KnowledgeStatus status = KnowledgeStatus.DRAFT;

    @Column(name = "confidence", length = 20)
    private String confidence;

    @Column(name = "related_case_ids", columnDefinition = "TEXT")
    private String relatedCaseIds;

    @Column(name = "related_rule_ids", columnDefinition = "TEXT")
    private String relatedRuleIds;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum SourceType {
        OFFICIAL_CURRENT,
        OFFICIAL_HISTORICAL,
        AUDIT,
        EBOOK,
        CAFE_ARTICLE,
        CAFE_COMMENT,
        CASE,
        OPERATOR_KNOWLEDGE,
        DERIVED_RULE,
        /** 운영 규칙·상담 가이드라인 (일반 규칙, DERIVED_RULE과 달리 사례 기반 도출이 아님). */
        RULE,
        /** 회원 공용방 채팅에서 갈무리한 항목(개인정보 제거·요약 통과 후). */
        CHAT_DISCOVERY,
        /** 질문요청 게시판(§5)에서 갈무리한 항목(운영자 답변·재현 요청 패턴). */
        REQUEST_DISCOVERY
    }

    public enum KnowledgeStatus {
        DRAFT,
        REVIEW,
        ACTIVE,
        ARCHIVED
    }
}