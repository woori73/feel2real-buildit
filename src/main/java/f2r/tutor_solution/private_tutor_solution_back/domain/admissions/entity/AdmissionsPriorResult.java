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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 지난해 입시결과 한 행(V1099). 대학 입학처 공식 자료에서 옮긴 값만 담고, 지표명은 원문 그대로 둔다.
 * 기본 행(metricLabel = "")은 모집인원·지원자·경쟁률·충원, 지표 행은 성적 지표 하나.
 * academicYear는 상담·승격 단위(2027), resultYear는 결과가 속한 학년도(2026)다.
 */
@Entity
@Table(name = "admissions_prior_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_admissions_prior_natural", columnNames = {"university_id", "natural_key"}),
        indexes = @Index(name = "idx_admissions_prior_univ_year", columnList = "university_id, academic_year, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsPriorResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prior_result_id")
    private Long priorResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "result_year", nullable = false)
    private Integer resultYear;

    /** SUSI / JEONGSI */
    @Column(name = "round_type", length = 10, nullable = false)
    private String roundType;

    @Column(name = "group_name", length = 10)
    private String groupName;

    @Column(name = "type_name", length = 200, nullable = false)
    private String typeName;

    @Column(name = "department_name", length = 200, nullable = false)
    private String departmentName;

    /** 원문 지표명. 기본 행은 빈 문자열. */
    @Column(name = "metric_label", length = 200, nullable = false)
    @Builder.Default
    private String metricLabel = "";

    /** GRADE / PERCENTILE / SCORE / OTHER */
    @Column(name = "metric_unit", length = 20)
    private String metricUnit;

    @Column(name = "metric_value", precision = 12, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "raw_value_text", length = 200)
    private String rawValueText;

    @Column(name = "recruit_count")
    private Integer recruitCount;

    @Column(name = "applicant_count")
    private Integer applicantCount;

    @Column(name = "competition_rate", precision = 8, scale = 2)
    private BigDecimal competitionRate;

    @Column(name = "extra_admitted_count")
    private Integer extraAdmittedCount;

    @Column(name = "linked_type_name", length = 200)
    private String linkedTypeName;

    @Column(name = "linked_department_name", length = 200)
    private String linkedDepartmentName;

    @Column(name = "natural_key", length = 64, nullable = false)
    private String naturalKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private AdmissionsOfficialMaterial source;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "evidence_pages", length = 100)
    private String evidencePages;

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

    public boolean isBaseRow() {
        return metricLabel == null || metricLabel.isEmpty();
    }

    /** 같은 전형·모집단위(·군)를 묶는 키. 기본 행과 지표 행이 같은 값을 가진다. */
    public String unitKey() {
        return roundType + "|" + (groupName == null ? "" : groupName) + "|" + typeName + "|" + departmentName;
    }
}
