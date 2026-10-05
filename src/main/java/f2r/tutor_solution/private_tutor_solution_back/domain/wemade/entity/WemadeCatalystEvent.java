package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Q4 Triple Catalyst 이벤트 (DEAL_CLOSE / NCW_LAUNCH / DIGITAL_ASSET_LAW).
 * 확정되지 않은 사건은 status=UNCONFIRMED 또는 target_date=NULL로 구분한다.
 * content_hash = sha256("CATALYST|catalyst_key|as_of_date").
 */
@Entity
@Table(name = "wemade_catalyst_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeCatalystEvent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 50, unique = true, nullable = false)
    private String catalystKey;
    @Column(length = 200, nullable = false)
    private String nameKo;
    /** SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED|UNCONFIRMED */
    @Builder.Default
    @Column(length = 30, nullable = false)
    private String status = "SCHEDULED";
    private LocalDate targetDate;
    @Column(length = 100)
    private String targetLabel;
    @Column(length = 2000)
    private String summary;
    /** 핵심 수치 JSON 배열 문자열. */
    @Column(length = 3000)
    private String keyFacts;
    /** A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인 */
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
