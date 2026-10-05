package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 킹넷 CNINFO 공식 공시. 원문 근거를 반드시 보존한다. AI가 중요 사실을 임의 확정하지 않는다. */
@Entity
@Table(name = "wemade_china_disclosures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeChinaDisclosure extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate announcedAt;
    @Column(length = 20)
    private String secCode;
    @Column(length = 100)
    private String secName;
    @Column(length = 1000, nullable = false)
    private String titleCn;
    @Column(length = 1000)
    private String titleKr;
    @Column(length = 3000)
    private String summaryKr;
    @Column(length = 2000)
    private String whyMatters;
    @Column(length = 50)
    private String announcementId;
    @Column(length = 1000)
    private String sourceUrl;
    /** 공식공시 기본 A */
    @Builder.Default
    @Column(length = 1)
    private String grade = "A";
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;
}
