package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * SOTP 시나리오 (보수적/기준/낙관적)와 공개 가정.
 * 단일 목표주가는 두지 않으며, 시나리오별 가정과 계산근거를 JSON으로 공개한다.
 */
@Entity
@Table(name = "wemade_valuation_scenarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeValuationScenario extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** CONSERVATIVE|BASE|OPTIMISTIC */
    @Column(length = 20, unique = true, nullable = false)
    private String scenarioKey;
    @Column(length = 100, nullable = false)
    private String nameKo;
    @Column(length = 2000)
    private String description;
    /** 시나리오 가정 (JSON 문자열) */
    @Column(columnDefinition = "TEXT")
    private String assumptionsJson;
}
