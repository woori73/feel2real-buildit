package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTransaction;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 세무 검토 서비스.
 *
 * 핵심 원칙: EASY 장부가 주체이고, TaxDoctor는 세무 검토 도구다.
 * - 기존 거래·분개를 임의로 수정/삭제하지 않는다
 * - MCP 장애 시에도 EASY 기능은 정상 작동한다
 * - 세법 판단이 필요한 거래만 선별적으로 질의한다
 * - 자동 수정을 하지 않는다 (사용자 승인 후에만 수정 API 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxReviewService {

    private final TaxDoctorMcpClient mcpClient;
    private final EasyTransactionRepository transactionRepository;
    private final TaxReviewResultRepository reviewResultRepository;

    /**
     * 세법 검토가 필요한 거래인지 판단.
     * 우선 검토 대상:
     * - 접대비·업무추진 관련 비용
     * - 복리후생비, 차량 관련 비용, 임차료, 관리비, 경조사비
     * - 고정자산·감가상각 후보
     * - 개인사용 가능성이 있는 카드거래
     * - 증빙 누락 거래
     * - 부가세 공제·불공제 판단 필요 거래
     * - 계정과목이 불확실한 거래
     */
    public boolean needsTaxReview(EasyTransaction txn) {
        if (txn == null) return false;

        String account = txn.getAccount() != null ? txn.getAccount().name() : "";
        String memo = txn.getMemo() != null ? txn.getMemo() : "";
        String taxClass = txn.getTaxClassification() != null ? txn.getTaxClassification().name() : "";

        // 접대비·복리후생·차량 관련
        if (account.contains("ENTERTAINMENT") || account.contains("WELFARE") ||
            account.contains("VEHICLE") || memo.contains("접대") ||
            memo.contains("회식") || memo.contains("차량")) {
            return true;
        }

        // 증빙 누락
        if (txn.getEvidenceType() == null || "NONE".equals(txn.getEvidenceType().name())) {
            return true;
        }

        // 계정과목 불확실 (미분류)
        if ("UNCATEGORIZED".equals(taxClass) || "UNCERTAIN".equals(taxClass)) {
            return true;
        }

        // 고액 거래 (100만원 이상)는 검토 후보
        if (txn.getAmount() >= 1_000_000) {
            return true;
        }

        return false;
    }

    /**
     * 단일 거래에 대한 세무 검토.
     * MCP 실패 시에도 예외를 던지지 않고 null을 반환한다.
     * 기존 거래를 수정하지 않는다.
     */
    @Transactional
    public TaxReviewResult reviewSingle(Long txnId, String reviewReason) {
        EasyTransaction txn = transactionRepository.findById(txnId).orElse(null);
        if (txn == null) {
            log.warn("Transaction not found: {}", txnId);
            return null;
        }

        // 개인정보 최소 전송 원칙
        TaxReviewRequest request = TaxReviewRequest.builder()
                .easyTxnId(txnId)
                .occurredOn(txn.getOccurredOn() != null ? txn.getOccurredOn().toString() : null)
                .amount(txn.getAmount())
                .memo(txn.getMemo())
                .businessType(txn.getBusiness() != null ? txn.getBusiness().name() : null)
                .currentAccount(txn.getAccount() != null ? txn.getAccount().name() : null)
                .currentTaxClassification(txn.getTaxClassification() != null ?
                        txn.getTaxClassification().name() : null)
                .evidenceType(txn.getEvidenceType() != null ?
                        txn.getEvidenceType().name() : null)
                .reviewReason(reviewReason)
                .build();

        TaxReviewResult result = mcpClient.reviewTransaction(request);

        if (result != null) {
            // 결과 저장 (별도 테이블, 기존 거래 수정 없음)
            TaxReviewRecord record = TaxReviewRecord.builder()
                    .txnId(txnId)
                    .businessType(request.getBusinessType())
                    .taxReviewStatus(result.getTaxReviewStatus())
                    .recommendedAccount(result.getRecommendedAccount())
                    .currentAccount(request.getCurrentAccount())
                    .deductibility(result.getDeductibility())
                    .vatDeductible(result.getVatDeductible())
                    .evidenceRequirement(result.getEvidenceRequirement())
                    .businessRelevance(result.getBusinessRelevance())
                    .depreciationCandidate(result.getDepreciationCandidate())
                    .riskLevel(result.getRiskLevel())
                    .reason(result.getReason())
                    .legalBasis(result.getLegalBasis())
                    .source(result.getSource())
                    .reviewedAt(result.getReviewedAt())
                    .userConfirmed(false)  // 사용자 승인 전
                    .build();
            reviewResultRepository.save(record);
        }

        return result;
    }

    /**
     * 검토가 필요한 거래 목록 조회 (기관별).
     */
    @Transactional(readOnly = true)
    public List<EasyTransaction> findNeedsReview(String businessType, int limit) {
        // 간단한 구현: 전체에서 필터링
        // 실제 운영에서는 쿼리 최적화 필요
        return transactionRepository.findAll().stream()
                .filter(t -> businessType == null ||
                        (t.getBusiness() != null && businessType.equals(t.getBusiness().name())))
                .filter(this::needsTaxReview)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * MCP 연결 테스트.
     */
    public boolean testMcpConnection() {
        return mcpClient.testConnection();
    }
}
