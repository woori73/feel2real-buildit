package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 이상수급 조합 분석 결과.
 *
 * 개별 지표(wemade_anomaly_metrics)의 수치를 조합해 분석 단계에서 생성한다.
 * 결론을 먼저 내리지 않고 FACT/ANALYSIS/HYPOTHESIS를 구분한다.
 *
 * 특히 "특정 거래원이나 외국인 매도가 중국 인수자와 관련됐다"는 추정은
 * 직접 증거가 없으면 절대 하지 않는다. relationNote 기본값 "연관성 확인되지 않음".
 */
@Entity
@Table(name = "wemade_anomaly_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeAnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목코드: 112040 */
    @Column(length = 20, nullable = false)
    private String symbol;

    /** 탐지 시각 */
    @Column(nullable = false)
    private LocalDateTime detectedAt;

    /** 분석 유형:
     * VOLUME_SURGE (거래량 급증),
     * LARGE_TRADES (대량체결 반복),
     * QUOTE_IMBALANCE (호가 불균형),
     * BROKER_CONCENTRATION (특정 회원사 집중),
     * FOREIGN_SELL_RALLY (급등 구간 외국인/기관 순매도),
     * BOLLINGER_SELL_PRESSURE (볼린저 상단 + 거래량 증가 + 매도수급),
     * POST_SURGE_MOVE (급등 후 가격 변화) */
    @Column(length = 40, nullable = false)
    private String analysisType;

    /** 분석 요약 (파생 지표 기반 서술, 원시 시세 미포함) */
    @Column(length = 1000)
    private String summary;

    /** 근거 수준: FACT(관측 데이터) / ANALYSIS(계산) / HYPOTHESIS(미확인 가설) */
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String evidenceLevel = "ANALYSIS";

    /** 시장 구분: KRX / NXT / UNIFIED / MULTI (복수 시장 관련 시). */
    @Column(length = 10)
    private String exchange;

    /** 연관성 메모. 기본값 "연관성 확인되지 않음". 직접 증거 없이 변경하지 않는다. */
    @Column(length = 500)
    @Builder.Default
    private String relationNote = "연관성 확인되지 않음";

    /** 참조 지표 ID 목록 (콤마 구분, wemade_anomaly_metrics) */
    @Column(length = 500)
    private String metricIds;

    /** 과거 유사 패턴: 매칭된 과거 급등일 수 */
    private Integer similarPastCount;

    /** 과거 유사 패턴 이후 평균 5거래일 수익률(%). 없으면 null. */
    private Double similarPastAvgRet5d;

    @Column(length = 64, unique = true)
    private String contentHash;

    private LocalDateTime createdAt;
}
