package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 급등일 수급 분석.
 * 급등일(예: +5%/+10%/+20% 또는 직전 고점 돌파)마다 외국인·기관 순매도량/금액과
 * 이후 1/3/5/10거래일 수익률을 DB화한다.
 * 수급 데이터가 PENDING인 동안은 행이 생성되지 않는다 (추정 금지).
 */
@Entity
@Table(name = "wemade_surge_analysis")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeSurgeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목코드: 112040 */
    @Column(length = 20, nullable = false)
    private String symbol;

    /** 급등일 */
    @Column(nullable = false)
    private LocalDate surgeDate;

    /** 급등 유형: SURGE_5 / SURGE_10 / SURGE_20 / BREAKOUT_HIGH */
    @Column(length = 20)
    private String surgeType;

    /** 당일 종가 */
    private Integer closePrice;

    /** 당일 등락률(%) */
    private Double changeRate;

    /** 외국인 순매수 금액(백만원, +순매수/-순매도). 데이터 없으면 null. */
    private Long foreignNetBuy;

    /** 기관 순매수 금액(백만원). 데이터 없으면 null. */
    private Long institutionNetBuy;

    /** 개인 순매수 금액(백만원). 데이터 없으면 null. */
    private Long retailNetBuy;

    /** 이후 1거래일 수익률(%) */
    private Double ret1d;

    /** 이후 3거래일 수익률(%) */
    private Double ret3d;

    /** 이후 5거래일 수익률(%) */
    private Double ret5d;

    /** 이후 10거래일 수익률(%) */
    private Double ret10d;

    /** 수급 데이터 출처: PYKRX / KIS / null(미확보) */
    @Column(length = 20)
    private String flowSource;

    @Column(length = 64, unique = true)
    private String contentHash;

    private LocalDateTime analyzedAt;
}
