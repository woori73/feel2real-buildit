package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_types",
        indexes = {
                @Index(name = "idx_admissions_type_univ_year", columnList = "university_id, academic_year"),
                @Index(name = "idx_admissions_type_name", columnList = "name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admission_type_id")
    private Long admissionTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private AdmissionsUniversity university;

    @Column(name = "academic_year", nullable = false)
    private Integer academicYear;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
