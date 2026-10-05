package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 중국 뉴스 (원문+번역). 공시와 충돌하면 공시를 우선하고 차이를 표시한다. */
@Entity
@Table(name = "wemade_china_news")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeChinaNews extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate publishedAt;
    @Column(length = 1000, nullable = false)
    private String titleCn;
    @Column(length = 1000)
    private String titleKr;
    @Column(length = 200)
    private String sourceName;
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 3000)
    private String summaryKr;
    @Column(length = 2000)
    private String whyMatters;
    /** 공식공시와 일치 여부 */
    private Boolean matchesDisclosure;
    /** B=중국 주요 경제매체, C=게임전문매체, D=커뮤니티/추정 */
    @Builder.Default
    @Column(length = 1)
    private String grade = "C";
    @Column(length = 500)
    private String keywords;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;
}
