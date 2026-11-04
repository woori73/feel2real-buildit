package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 수시 수능최저학력기준 한 행(V1101). RULE은 조건식(참이면 충족), NO_MINIMUM은 최저가 없는 전형(원문 근거만).
 * typeNames·departmentNames·uncertainJson은 JSON 문자열.
 */
@Entity
@Table(name = "admissions_susi_minimums",
        uniqueConstraints = @UniqueConstraint(name = "uk_admissions_susi_minimum",
                columnNames = {"university_id", "academic_year", "rule_key"}),
        indexes = @Index(name = "idx_admissions_susi_min_univ_year", columnList = "university_id, academic_year, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsSusiMinimum {

    public enum RuleKind { RULE, NO_MINIMUM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "minimum_id")
    private Long minimumId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "rule_key", length = 150, nullable = false)
    private String ruleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_kind", length = 20, nullable = false)
    private RuleKind ruleKind;

    @Column(name = "label", length = 300, nullable = false)
    private String label;

    @Column(name = "type_names", columnDefinition = "TEXT", nullable = false)
    private String typeNames;

    @Column(name = "department_names", columnDefinition = "TEXT")
    private String departmentNames;

    @Column(name = "unit_scope_text", columnDefinition = "TEXT")
    private String unitScopeText;

    @Column(name = "condition_expr", columnDefinition = "TEXT")
    private String conditionExpr;

    @Column(name = "required_areas_text", columnDefinition = "TEXT")
    private String requiredAreasText;

    @Column(name = "evidence_pages", length = 200)
    private String evidencePages;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "uncertain_json", columnDefinition = "TEXT")
    private String uncertainJson;

    @Column(name = "needs_review_reason", columnDefinition = "TEXT")
    private String needsReviewReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private AdmissionsOfficialMaterial source;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AdmissionsKnowledgeItem.KnowledgeStatus status = AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
