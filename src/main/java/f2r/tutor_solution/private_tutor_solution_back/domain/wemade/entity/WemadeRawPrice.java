package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDate;

/** 일봉 RAW (3종목). 지표는 서버에서 deterministic 계산. 통화: 112040/101730=KRW, 002517=CNY. */
@Entity
@Table(name = "wemade_raw_prices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(WemadeRawPrice.PriceId.class)
public class WemadeRawPrice {
    /** 종목코드: 112040(위메이드), 101730(위메이드맥스), 002517(킹넷) */
    @Id
    @Column(length = 20)
    private String symbol;
    @Id
    private LocalDate tradeDate;
    private Integer openPrice;
    private Integer highPrice;
    private Integer lowPrice;
    /** 종가. 002517은 CNY(위안), 나머지는 KRW. */
    private Integer closePrice;
    /** 킹넷(CNY 소수점)은 100배 스케일로 저장한다. */
    private Long volume;
    @Builder.Default
    private String source = "NAVER_FCHART";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private java.time.LocalDateTime collectedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class PriceId implements Serializable {
        private String symbol;
        private LocalDate tradeDate;
    }
}
