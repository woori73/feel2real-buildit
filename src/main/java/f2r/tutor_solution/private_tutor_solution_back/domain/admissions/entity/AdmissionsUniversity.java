package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_universities",
        indexes = {
                @Index(name = "idx_admissions_univ_region", columnList = "region")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsUniversity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "university_id")
    private Long universityId;

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "type", length = 20)
    private String type;

    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
