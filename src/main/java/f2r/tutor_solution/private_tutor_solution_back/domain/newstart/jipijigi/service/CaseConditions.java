package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import lombok.Builder;
import lombok.Getter;

/**
 * 질문에서 추출한 구조화 검색 조건.
 *
 * <p>모든 필드는 nullable — 추출 실패 시 null이며, null인 조건은 검색 필터에 사용하지 않는다.
 * 조건 추출에 실패해도 기존 LIKE 검색으로 폴백하므로, 이 객체가 비어 있는 것은 정상 상태다.
 *
 * <p>미확인 조건을 임의로 채우지 않는다: 예를 들어 질문에 가구유형이 없으면 householdType은 null로 두고
 * 필터링하지 않는다.
 */
@Getter
@Builder
public class CaseConditions {

    /** 가구유형 (예: "3인가구"). 질문에서 "(\d)인가구" 패턴으로 추출. */
    private final String householdType;

    /** 채무 총액 하한 (원). 질문에서 "(\d+)만원" 등으로 추출. null이면 필터 없음. */
    private final Long debtTotalMin;

    /** 채무 총액 상한 (원). null이면 필터 없음. */
    private final Long debtTotalMax;

    /** 소득 하한 (원, 연소득 기준). null이면 필터 없음. */
    private final Long incomeMin;

    /** 소득 상한 (원, 연소득 기준). null이면 필터 없음. */
    private final Long incomeMax;

    /** 감면율 하한 (%). "75%" → 75. VARCHAR 파싱 실패 시 null (임의 제외 금지). */
    private final Integer reductionRateMin;

    /** 감면율 상한 (%). null이면 필터 없음. */
    private final Integer reductionRateMax;

    /** 담보대출 보유 여부. true=담보 있음, false=담보 없음, null=조건 없음. */
    private final Boolean hasSecuredDebt;

    /** 신청 유형: "INSOLVENCY"(부실차주) / "INSOLVENCY_RISK"(부실우려차주). null이면 조건 없음. */
    private final String applicationType;

    /** 재산(부동산) 하한 (원). null이면 필터 없음. */
    private final Long assetPropertyMin;

    /** 재산(부동산) 상한 (원). null이면 필터 없음. */
    private final Long assetPropertyMax;

    /** 조건이 하나라도 있는지. 비어 있으면 구조화 필터를 적용하지 않는다. */
    public boolean isEmpty() {
        return householdType == null
                && debtTotalMin == null && debtTotalMax == null
                && incomeMin == null && incomeMax == null
                && reductionRateMin == null && reductionRateMax == null
                && hasSecuredDebt == null
                && applicationType == null
                && assetPropertyMin == null && assetPropertyMax == null;
    }
}
