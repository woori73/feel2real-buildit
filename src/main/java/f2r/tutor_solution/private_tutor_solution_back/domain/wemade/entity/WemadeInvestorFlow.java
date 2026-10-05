package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 투자자별 수급 RAW. 매일 수집해 자체 장기 DB로 누적한다. */
@Entity
@Table(name = "wemade_investor_flows")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(WemadeInvestorFlow.FlowId.class)
public class WemadeInvestorFlow {
    @Id
    private LocalDate tradeDate;
    /** FOREIGN, INSTITUTION, RETAIL */
    @Id
    @Column(length = 20)
    private String investorType;
    /** 순매수 금액(백만원, +순매수/-순매도) */
    private Long netBuyAmount;
    private Long buyAmount;
    private Long sellAmount;
    @Builder.Default
    private String source = "NAVER";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class FlowId implements Serializable {
        private LocalDate tradeDate;
        private String investorType;
    }
}
