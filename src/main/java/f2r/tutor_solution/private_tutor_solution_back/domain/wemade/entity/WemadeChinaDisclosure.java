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
    /** ④ 원문 흐름을 따른 상세 한국어 번역·해설 (전문 복제 금지) */
    @Column(columnDefinition = "TEXT")
    private String detailKr;
    /** ⑤ 금액·지분·회사·승인기관·일정 핵심 사실 */
    @Column(columnDefinition = "TEXT")
    private String keyFactsKr;
    /** ⑥ 한국/중국 공시 대조 */
    @Column(columnDefinition = "TEXT")
    private String crossCheckKr;
    /** ⑦ 공시 확인 사실 vs 언론 해석·주장 구분 */
    @Column(columnDefinition = "TEXT")
    private String factVsClaimKr;
    /** 관련 공시 참조 */
    @Column(length = 1000)
    private String relatedRefs;
    /** ⑨ 원문 링크 상태: OK/BROKEN/BLOCKED/UNKNOWN */
    @Column(length = 20)
    private String linkStatus;
    private LocalDateTime linkCheckedAt;

    /** 목록에서 '한국어로 자세히 보기' 버튼 표시 여부 */
    @com.fasterxml.jackson.annotation.JsonProperty("hasDetail")
    public boolean hasDetail() {
        return detailKr != null && !detailKr.isBlank();
    }
}
