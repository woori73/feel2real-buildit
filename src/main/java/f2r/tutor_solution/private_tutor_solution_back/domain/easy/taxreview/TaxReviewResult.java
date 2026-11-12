package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * TaxDoctor MCP로부터 반환받는 세무 검토 결과.
 * TaxDoctor가 반환하지 않은 정보는 EASY가 임의로 생성하지 않는다.
 */
@Value
@Builder
public class TaxReviewResult {
    /** 검토 상태: REVIEWED / NEEDS_EVIDENCE / UNCERTAIN / FAILED */
    String taxReviewStatus;

    /** 추천 계정과목 */
    String recommendedAccount;

    /** 손금산입 가능 여부: DEDUCTIBLE / NON_DEDUCTIBLE / PARTIAL / UNCERTAIN */
    String deductibility;

    /** 부가세 공제 가능 여부 */
    Boolean vatDeductible;

    /** 필요 증빙 */
    String evidenceRequirement;

    /** 사업관련성: HIGH / MEDIUM / LOW / UNCERTAIN */
    String businessRelevance;

    /** 감가상각 후보 여부 */
    Boolean depreciationCandidate;

    /** 위험도: SAFE / CAUTION / WARNING / HIGH_RISK */
    String riskLevel;

    /** 판단 이유 */
    String reason;

    /** 법적 근거 (법령·시행령·예규·판례 등) */
    String legalBasis;

    /** 근거 출처 */
    String source;

    /** 검토 시각 */
    LocalDateTime reviewedAt;

    /** MCP 원본 응답 (디버깅용) */
    String rawResponse;
}
