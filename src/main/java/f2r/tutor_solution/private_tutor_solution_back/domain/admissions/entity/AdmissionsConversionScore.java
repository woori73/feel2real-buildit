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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 대학 탐구 변환표준점수표 한 행(V1102) — 대학·학년도·계열(SOC/SCI/VOC)마다 하나. scoresJson은 백분위("0"~"100") → 점수. */
@Entity
@Table(name = "admissions_conversion_scores",
        uniqueConstraints = @UniqueConstraint(name = "uk_admissions_conversion_score",
                columnNames = {"university_id", "academic_year", "track"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsConversionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversion_id")
    private Long conversionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "track", length = 10, nullable = false)
    private String track;

    @Column(name = "label", length = 100, nullable = false)
    private String label;

    @Column(name = "scores_json", columnDefinition = "TEXT", nullable = false)
    private String scoresJson;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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
