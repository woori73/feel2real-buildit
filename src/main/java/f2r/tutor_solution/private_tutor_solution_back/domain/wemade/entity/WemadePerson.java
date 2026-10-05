package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 핵심 인물 심층 프로필.
 * 확인된 사실과 미확인을 필드 단위로 분리한다. 공식자료 없는 연결은 기록하지 않는다.
 */
@Entity
@Table(name = "wemade_persons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadePerson extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String personKey;
    @Column(length = 200, nullable = false)
    private String name;
    /** 중국어 성명 표기. 미확인 시 null (추측 표기 금지). */
    @Column(length = 200)
    private String nameZh;
    @Column(length = 3000)
    private String bioKo;
    @Column(length = 500)
    private String education;
    @Column(length = 1000)
    private String affiliations;
    @Column(length = 2000)
    private String pastRoles;
    /** 킹넷 및 킹넷 관계자와의 과거 관계 */
    @Column(length = 2000)
    private String relationKingnet;
    /** 박관호 의장과의 기존 관계 */
    @Column(length = 2000)
    private String relationPark;
    /** 미르/传奇 IP 관련 과거 이해관계 */
    @Column(length = 2000)
    private String relationMirIp;
    /** 교차검증 결과 및 한계 */
    @Column(length = 2000)
    private String verificationNote;
    /** A=공식공시, B=복수신뢰매체, C=단일매체, D=계산값, U=미확인 */
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
