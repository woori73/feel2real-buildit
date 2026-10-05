package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 세무 검토 결과 저장 엔티티.
 * 기존 EasyTransaction을 수정하지 않고 별도 테이블에 저장한다.
 */
@Entity
@Table(name = "easy_tax_review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** EASY 거래 ID (FK, 수정하지 않음) */
    @Column(nullable = false)
    private Long txnId;

    /** 기관: ACADEMY / PUBLISHING */
    @Column(length = 20)
    private String businessType;

    /** 검토 상태 */
    @Column(length = 30)
    private String taxReviewStatus;

    /** 세법 추천 계정과목 */
    @Column(length = 50)
    private String recommendedAccount;

    /** 현재 EASY 계정과목 (검토 시점) */
    @Column(length = 50)
    private String currentAccount;

    /** 손금산입 가능 여부 */
    @Column(length = 20)
    private String deductibility;

    /** 부가세 공제 가능 여부 */
    private Boolean vatDeductible;

    /** 필요 증빙 */
    @Column(length = 500)
    private String evidenceRequirement;

    /** 사업관련성 */
    @Column(length = 20)
    private String businessRelevance;

    /** 감가상각 후보 여부 */
    private Boolean depreciationCandidate;

    /** 위험도 */
    @Column(length = 20)
    private String riskLevel;

    /** 판단 이유 */
    @Column(length = 2000)
    private String reason;

    /** 법적 근거 */
    @Column(length = 2000)
    private String legalBasis;

    /** 근거 출처 */
    @Column(length = 500)
    private String source;

    /** 검토 시각 */
    private LocalDateTime reviewedAt;

    /** 사용자 승인 여부 (승인 시에만 EASY 수정 API 사용) */
    @Builder.Default
    private Boolean userConfirmed = false;

    /** 승인 시각 */
    private LocalDateTime confirmedAt;

    /** 생성 시각 */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
