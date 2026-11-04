package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/** 수능 표준점수 최고점 한 행(V1102). subjectKey = KOR / MATH / INQ:{원문 과목명}. 평가원 채점 결과 원문 값. */
@Entity
@Table(name = "admissions_csat_std_max",
        uniqueConstraints = @UniqueConstraint(name = "uk_admissions_csat_std_max", columnNames = {"academic_year", "subject_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsCsatStdMax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "std_max_id")
    private Long stdMaxId;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "subject_key", length = 100, nullable = false)
    private String subjectKey;

    @Column(name = "area", length = 10, nullable = false)
    private String area;

    @Column(name = "subject_name", length = 100)
    private String subjectName;

    @Column(name = "track", length = 10)
    private String track;

    @Column(name = "std_max", precision = 6, scale = 2, nullable = false)
    private BigDecimal stdMax;

    @Column(name = "evidence_pages", length = 200)
    private String evidencePages;

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
