package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 주장별 독립 Fact Check. 각 주장을 등급(A/B/C/D/U)과 함께 관리한다. */
@Entity
@Table(name = "wemade_fact_checks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeFactCheck extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String claimKey;
    @Column(length = 500, nullable = false)
    private String claim;
    /** A=공식1차, B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인 */
    @Builder.Default
    @Column(length = 1)
    private String grade = "U";
    @Column(length = 2000)
    private String verdict;
    @Column(length = 2000)
    private String basis;
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    private LocalDateTime reviewedAt;
}
