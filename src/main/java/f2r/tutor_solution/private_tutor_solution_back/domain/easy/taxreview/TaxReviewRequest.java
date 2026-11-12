package f2r.tutor_solution.private_tutor_solution_back.domain.easy.taxreview;

import lombok.Builder;
import lombok.Value;

/**
 * TaxDoctor MCP에 전송하는 세무 검토 요청.
 * 개인정보 최소 전송 원칙: 거래일·금액·가맹점·기관·현재 계정과목·증빙 종류만 전달.
 * 주민번호, 계좌번호 전체, 카드번호, 로그인 정보 등은 절대 포함하지 않는다.
 */
@Value
@Builder
public class TaxReviewRequest {
    /** EASY 거래 ID (내부 참조용, MCP에는 전달하지 않음) */
    Long easyTxnId;

    /** 거래일 */
    String occurredOn;

    /** 금액 */
    long amount;

    /** 가맹점/적요 */
    String memo;

    /** 기관 구분: ACADEMY / PUBLISHING */
    String businessType;

    /** 현재 EASY 계정과목 */
    String currentAccount;

    /** 현재 세무 분류 */
    String currentTaxClassification;

    /** 증빙 종류 */
    String evidenceType;

    /** 검토 사유 (왜 세법 판단이 필요한지) */
    String reviewReason;
}
