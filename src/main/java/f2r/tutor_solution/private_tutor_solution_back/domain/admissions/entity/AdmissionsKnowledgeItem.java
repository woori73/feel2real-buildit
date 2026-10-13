package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 입시상담 지식베이스 — 지피지기의 NewstartKnowledgeItem 패턴을 입시 도메인으로 적용.
 *
 * <p>승인 상태: DRAFT → REVIEW → ACTIVE. ACTIVE만 RAG 검색 대상.
 * <p>APPROVED_CANDIDATE를 ACTIVE로 임의 변경 금지 — 관리자 승인 API를 통해서만 전환.
 * <p>source_class: OFFICIAL(공식자료) / YOUTUBE(유튜브 의견) / SIMULATION(상담 시뮬레이션).
 * YOUTUBE 자료는 확정 사실 답변에 사용 금지.
 */
@Entity
@Table(name = "admissions_knowledge_items",
        indexes = {
                @Index(name = "idx_admissions_knowledge_status", columnList = "status, source_class"),
                @Index(name = "idx_admissions_knowledge_year", columnList = "academic_year, year_confirmed"),
                @Index(name = "idx_admissions_knowledge_category", columnList = "category")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsKnowledgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_id")
    private Long knowledgeId;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "year_confirmed", nullable = false)
    @Builder.Default
    private Boolean yearConfirmed = false;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_class", length = 20, nullable = false)
    private SourceClass sourceClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private AdmissionsOfficialMaterial source;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private KnowledgeStatus status = KnowledgeStatus.DRAFT;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verificationNote;

    @Column(name = "dedup_key", length = 191, unique = true)
    private String dedupKey;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

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

    public enum SourceClass {
        /** 공식 모집요강·입학처 자료 */
        OFFICIAL,
        /** 유튜브 분석 의견 — 확정 사실 답변에 사용 금지 */
        YOUTUBE,
        /** 상담 시뮬레이션 — 테스트용, RAG 직접 대상 아님 */
        SIMULATION
    }

    public enum KnowledgeStatus {
        DRAFT,
        REVIEW,
        ACTIVE,
        ARCHIVED
    }
}
