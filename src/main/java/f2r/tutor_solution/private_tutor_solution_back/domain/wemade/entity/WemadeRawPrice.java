package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** 일봉 RAW. 지표(MA/RSI/MACD)는 서버에서 deterministic 계산하므로 저장하지 않는다. */
@Entity
@Table(name = "wemade_raw_prices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeRawPrice {
    @Id
    private LocalDate tradeDate;
    private Integer openPrice;
    private Integer highPrice;
    private Integer lowPrice;
    private Integer closePrice;
    private Long volume;
    @Builder.Default
    private String source = "NAVER_FCHART";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private java.time.LocalDateTime collectedAt;
}
