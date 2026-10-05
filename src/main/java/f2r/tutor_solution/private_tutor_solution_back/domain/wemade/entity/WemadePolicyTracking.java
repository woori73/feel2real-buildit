package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 디지털자산기본법 입법 추적. 국회·금융위 공식 자료 기반.
 * 법안 통과는 확정 사건으로 표시하지 않는다 (isConfirmed=0 → UI 미확정 딱지).
 * content_hash = sha256("POLICY|policy_key|as_of_date").
 */
@Entity
@Table(name = "wemade_policy_tracking")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadePolicyTracking extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String policyKey;
    @Column(length = 500, nullable = false)
    private String title;
    /** TRACKING|발의|상임위|법사위|본회의|공포|시행 */
    @Builder.Default
    @Column(length = 30, nullable = false)
    private String stage = "TRACKING";
    private LocalDate stageDate;
    @Column(length = 2000)
    private String summary;
    /** P2E/게임산업법 연관성 메모. */
    @Column(length = 1000)
    private String p2eRelevance;
    /** 확정 사건 여부. false면 UI에 미확정 딱지. */
    @Builder.Default
    private Boolean isConfirmed = false;
    /** A=공식1차(국회/금융위), B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인 */
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
