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
