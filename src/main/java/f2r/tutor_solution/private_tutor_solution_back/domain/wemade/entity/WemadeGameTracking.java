package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Night Crows W / 중국 Tracker. OFFICIAL/PRESS/COMMUNITY를 반드시 분리한다. */
@Entity
@Table(name = "wemade_game_tracking")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeGameTracking extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate trackedAt;
    @Column(length = 200)
    private String gameName;
    /** PANHO, PUBLISHER, PREREG, CBT, LAUNCH, RANK_APPSTORE, RANK_GP, RANK_REVENUE, ANNOUNCE */
    @Column(length = 100)
    private String item;
    @Column(length = 2000)
    private String detail;
    /** OFFICIAL, PRESS, COMMUNITY */
    @Column(length = 20)
    private String sourceType;
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;
}
