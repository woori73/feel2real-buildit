package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 회원사(거래원) 시간별 스냅샷.
 *
 * 장중 회원사 데이터는 시간별 스냅샷으로 축적한다. 장 마감 누적값만 저장하지 않고,
 * 특정 상승구간에서 어느 회원사가 매도·매수를 늘렸는지 나중에 재구성할 수 있게 한다.
 * 최소 30분 간격, 급등 시 5분 간격으로 스냅샷을 저장한다.
 *
 * KIS 원시 응답은 저장하지 않고 회원사명·수량 파생값만 저장한다 (INTERNAL).
 * 특정 거래원과 중국 인수자의 연관성은 직접 증거 없이 추정하지 않는다.
 * 연관성 미확인 시 relationNote는 "연관성 확인되지 않음"으로 둔다.
 */
@Entity
@Table(name = "wemade_member_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeMemberSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목코드: 112040 */
    @Column(length = 20, nullable = false)
    private String symbol;

    /** 측정 시각 */
    @Column(nullable = false)
    private LocalDateTime measuredAt;

    /** 회원사명 (KIS mbcr_name) */
    @Column(length = 60, nullable = false)
    private String brokerName;

    /** 매수량 (주) */
    private Long buyVolume;

    /** 매도량 (주) */
    private Long sellVolume;

    /** 순매수량 (주, +순매수/-순매도) */
    private Long netVolume;

    /** 매수금액 (백만원) */
    private Long buyAmount;

    /** 매도금액 (백만원) */
    private Long sellAmount;

    /** 데이터 출처: KIS */
    @Column(length = 20)
    @Builder.Default
    private String source = "KIS";

    /** 시장 구분: KRX / NXT / UNIFIED. KIS에서 실제 제공되는 범위대로 저장한다.
     *  제공되지 않는 항목을 다른 시장 데이터로 임의 대체하지 않는다. */
    @Column(length = 10)
    @Builder.Default
    private String exchange = "KRX";

    /** 세션 구분: PRE / REGULAR / AFTER / CLOSE (KIS MARKET_CLS_CODE 기준). */
    @Column(length = 10)
    private String session;

    /** 연관성 메모. 기본값 "연관성 확인되지 않음". 직접 증거 없이 변경하지 않는다. */
    @Column(length = 200)
    @Builder.Default
    private String relationNote = "연관성 확인되지 않음";

    @Column(length = 64, unique = true)
    private String contentHash;

    private LocalDateTime createdAt;
}
