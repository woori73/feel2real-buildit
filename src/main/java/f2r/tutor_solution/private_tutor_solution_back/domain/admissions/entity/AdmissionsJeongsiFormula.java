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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 정시 수능 환산 계산식 하나(V1100). 대학 정시 모집요강의 수능 반영 방법을 계산식·등급표로 옮긴 것이다.
 * typeNames·departmentNames·tablesJson·uncertainJson은 JSON 문자열로 둔다.
 * needsReviewReason이 있으면 대학 확인 전에는 점수를 안내하지 않는다.
 */
@Entity
@Table(name = "admissions_jeongsi_formulas",
        uniqueConstraints = @UniqueConstraint(name = "uk_admissions_jeongsi_formula",
                columnNames = {"university_id", "academic_year", "formula_key"}),
        indexes = @Index(name = "idx_admissions_jeongsi_univ_year", columnList = "university_id, academic_year, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsJeongsiFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "formula_id")
    private Long formulaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "formula_key", length = 100, nullable = false)
    private String formulaKey;

    @Column(name = "label", length = 300, nullable = false)
    private String label;

    @Column(name = "type_names", columnDefinition = "TEXT", nullable = false)
    private String typeNames;

    @Column(name = "department_names", columnDefinition = "TEXT")
    private String departmentNames;

    @Column(name = "unit_scope_text", columnDefinition = "TEXT")
    private String unitScopeText;

    @Column(name = "csat_points", precision = 10, scale = 3)
    private BigDecimal csatPoints;

    @Column(name = "perfect_score_expected", precision = 10, scale = 3)
    private BigDecimal perfectScoreExpected;

    @Column(name = "expression", columnDefinition = "TEXT", nullable = false)
    private String expression;

    @Column(name = "tables_json", columnDefinition = "TEXT")
    private String tablesJson;

    @Column(name = "rounding_text", length = 500)
    private String roundingText;

    @Column(name = "eligibility_text", columnDefinition = "TEXT")
    private String eligibilityText;

    @Column(name = "not_in_csat_score_text", columnDefinition = "TEXT")
    private String notInCsatScoreText;

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
