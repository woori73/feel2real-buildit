package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** 주가 결합 이벤트. 클릭 시 사건->원자료->당시 주가->이후 1/3/5/20거래일 변화를 보여준다. */
@Entity
@Table(name = "wemade_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeEvent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate eventDate;
    /** ACQUISITION, PANHO, GAME, EARNINGS, DISCLOSURE, PRICE, OTHER */
    @Column(length = 50)
    private String category;
    @Column(length = 500, nullable = false)
    private String title;
    @Column(length = 2000)
    private String summary;
    /** OFFICIAL, PRESS, COMMUNITY */
    @Column(length = 50)
    private String source;
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    /** A=공식1차, B=복수신뢰, C=언론, D=계산/커뮤니티, U=미확인 */
    @Builder.Default
    @Column(length = 1)
    private String factGrade = "U";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
}
