package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 지배관계 간선 (parent -> child). 지분율과 등급/출처를 함께 관리한다. */
@Entity
@Table(name = "wemade_ownership_edges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeOwnershipEdge extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 지배 측 노드 키 */
    @Column(length = 100, nullable = false)
    private String parentKey;
    /** 피지배 측 노드 키 */
    @Column(length = 100, nullable = false)
    private String childKey;
    /** 보유 지분율(%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal stakePct;
    @Column(length = 1000)
    private String note;
    /** A=공식공시, B=복수신뢰매체, C=단일매체, D=계산값, U=미확인 */
    @Builder.Default
    @Column(length = 1)
    private String grade = "U";
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    private LocalDate asOfDate;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
}
