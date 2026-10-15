package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 위메이드 SOTP 가치평가 구성요소. 시나리오별 low/base/high(원 단위).
 * 값은 리서치로 확정된 것만 저장하며, 미확인 항목은 NULL(미산정)로 둔다.
 * content_hash = sha256("component_key|as_of_date|value_base_krw").
 */
@Entity
@Table(name = "wemade_valuation_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeValuationComponent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String componentKey;
    @Column(length = 200, nullable = false)
    private String nameKo;
    /** GAME_IP|LISTED_STAKE|SUBSIDIARY|CASH|DEBT|OPTION */
    @Column(length = 20, nullable = false)
    private String category;
    /** 원 단위. NULL 허용. */
    private Long valueLowKrw;
    /** 원 단위. NULL=미산정. */
    private Long valueBaseKrw;
    /** 원 단위. NULL=미산정. */
    private Long valueHighKrw;
    @Column(length = 2000)
    private String basis;
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    private LocalDate asOfDate;
    /** A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인 */
    @Builder.Default
    @Column(length = 1)
    private String grade = "U";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
}
