package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 지배구조 노드 (인물/법인). 사실 등급(A/B/C/D/U)과 출처를 함께 관리한다. */
@Entity
@Table(name = "wemade_ownership_nodes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeOwnershipNode extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String nodeKey;
    @Column(length = 200, nullable = false)
    private String name;
    /** 중국어 표기. 미확인 시 null (추측 표기 금지). */
    @Column(length = 200)
    private String nameZh;
    /** PERSON|COMPANY */
    @Column(length = 20, nullable = false)
    private String nodeType;
    /** HK|KY|CN|KR */
    @Column(length = 10)
    private String country;
    /** 직속 하위 노드 보유 지분율(%). */
    @Column(precision = 10, scale = 4)
    private BigDecimal stakePct;
    @Column(length = 1000)
    private String stakeNote;
    @Column(length = 1000)
    private String roleDesc;
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
