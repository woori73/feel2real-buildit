package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * 가격괴리(SPA 9,200억 vs 시총) 요인. 경쟁가설 ①~⑥에 매핑(hypothesisNo).
 * amountLow/HighKrw가 NULL이면 미산정. 가정 기반 수치는 basis에 가정을 명시한다.
 * ⑥(인수 이해관계자-수급 연관성)은 직접 증거 전까지 미확인(U)으로 유지한다.
 * content_hash = sha256("GAP_FACTOR|factor_key|as_of_date").
 */
@Entity
@Table(name = "wemade_gap_factors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeGapFactor extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, unique = true, nullable = false)
    private String factorKey;
    /** 경쟁가설 번호 1~6 */
    private Integer hypothesisNo;
    @Column(length = 200, nullable = false)
    private String nameKo;
    /** 원 단위. NULL=미산정. */
    private Long amountLowKrw;
    /** 원 단위. NULL=미산정. */
    private Long amountHighKrw;
    @Column(length = 2000)
    private String basis;
    /** A=공식1차, B=복수신뢰, C=언론/증권사, D=계산/커뮤니티, U=미확인 */
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
