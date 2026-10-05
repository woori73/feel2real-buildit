package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 2026-06-29 이후 가격 이벤트 타임라인. OHLCV는 시드(prices.json) 실측값만 저장한다.
 * priceChangePct 등은 "전일 종가 대비" 실측 계산값이다. 수급 데이터는 저장하지 않는다
 * (미확보 — 절대 창작 금지).
 * content_hash = sha256("PRICE_EVENT|event_date|title").
 */
@Entity
@Table(name = "wemade_price_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadePriceEvent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate eventDate;
    @Column(length = 500, nullable = false)
    private String title;
    @Column(length = 2000)
    private String summary;
    /** 전일 종가 대비 등락률(%). 시드 OHLCV 실측. */
    @Column(precision = 8, scale = 2)
    private BigDecimal priceChangePct;
    private Integer closePrice;
    private Integer highPrice;
    private Integer lowPrice;
    private Long volume;
    /** DEAL|POLICY|NEWS|SURGE|CRASH|VOLUME_SPIKE|MILESTONE */
    @Column(length = 30, nullable = false)
    private String eventType;
    /** A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인 */
    @Builder.Default
    @Column(length = 1)
    private String grade = "U";
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
}
