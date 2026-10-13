package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_official_materials",
        indexes = {
                @Index(name = "idx_admissions_mat_univ_year", columnList = "university_id, academic_year"),
                @Index(name = "idx_admissions_mat_year", columnList = "academic_year")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsOfficialMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Long materialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "issuer", length = 200, nullable = false)
    private String issuer;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "file_key", length = 500)
    private String fileKey;

    /** 승인 상태. 승인(ACTIVE) 전 행은 상담 검색에 쓰이지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AdmissionsKnowledgeItem.KnowledgeStatus status = AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
