package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 수집 실행 기록. source별 fetched/accepted/duplicate/error. 실패한 source가 전체를 rollback하지 않는다. */
@Entity
@Table(name = "wemade_collection_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeCollectionRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** PRICE, FLOW, DISCLOSURE, GAME, FACTCHECK */
    @Column(length = 50, nullable = false)
    private String source;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @Builder.Default
    private Integer fetched = 0;
    @Builder.Default
    private Integer accepted = 0;
    @Builder.Default
    private Integer duplicate = 0;
    @Builder.Default
    private Integer error = 0;
    /** RUNNING, SUCCESS, PARTIAL, FAILED */
    @Builder.Default
    @Column(length = 20)
    private String status = "RUNNING";
    @Column(length = 500)
    private String message;
}
