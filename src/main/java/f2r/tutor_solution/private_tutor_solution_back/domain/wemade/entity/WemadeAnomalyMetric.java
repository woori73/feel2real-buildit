package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 이상수급 지표 (원시 수치 보관).
 *
 * 원칙: 결론부터 내리지 않는다. 거래량 급증 / 호가 불균형 / 외국인·기관 수급 /
 * 회원사 집중 / 가격 위치 / Bollinger를 각각 수치로 저장하고 조합은 별도 분석 단계에서 수행한다.
 * 임계값 초과 여부를 boolean이 아닌 원시 수치로 보관한다.
 *
 * KIS 원시 틱 데이터는 DB에 저장하지 않는다 (PRIVATE). 여기에는 파생 지표만 저장한다 (INTERNAL).
 */
@Entity
@Table(name = "wemade_anomaly_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeAnomalyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목코드: 112040 */
    @Column(length = 20, nullable = false)
    private String symbol;

    /** 측정 시각 */
    @Column(nullable = false)
    private LocalDateTime measuredAt;

    /** 지표 구분:
     * VOL_SURGE_1M / VOL_SURGE_5M / VOL_SURGE_10M (배수, 1.0=평상시),
     * LARGE_TRADE_COUNT (대량체결 건수),
     * QUOTE_IMBALANCE (-1~+1, +매수우위),
     * BROKER_TOP1_SELL_RATIO / BROKER_TOP1_BUY_RATIO (0~1),
     * FOREIGN_NET_AMT / INSTITUTION_NET_AMT (백만원, 당일 누적),
     * PRICE_POSITION (당일 (현재가-저가)/(고가-저가), 0~1),
     * BOLLINGER_PCTB (0~1, 1 초과 가능),
     * RSI_14, MACD_HIST */
    @Column(length = 40, nullable = false)
    private String metricType;

    /** 지표 원시 수치 (boolean이 아닌 실수) */
    private Double metricValue;

    /** 비교 기준값 (예: 평상시 평균 거래량). 없으면 null. */
    private Double baselineValue;

    /** 데이터 출처: KIS_WS / KIS_REST / PYKRX / CALC */
    @Column(length = 20)
    private String source;

    /** 시장 구분: KRX / NXT / UNIFIED (통합). 기본값 KRX (기존 데이터 호환). */
    @Column(length = 10)
    @Builder.Default
    private String exchange = "KRX";

    /** 세션 구분: PRE / REGULAR / AFTER / CLOSE (KIS MARKET_CLS_CODE 기준). */
    @Column(length = 10)
    private String session;

    /** 근거 수준: FACT(관측) / ANALYSIS(계산) */
    @Column(length = 20)
    @Builder.Default
    private String evidenceLevel = "ANALYSIS";

    @Column(length = 64, unique = true)
    private String contentHash;

    private LocalDateTime createdAt;
}
