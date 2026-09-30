package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 갈무리 DISPLAY 변환 계약 (2026-09-30 사용자 지시):
 * RAG용 structured 원본(## 분류/RESULT_CONFIRMED/UNKNOWN)은 사용자에게 그대로 출력하지 않고,
 * 상황/결과/원문/주의 구조의 사람용 텍스트로 재조립한다. 원문이 지지하는 사실만 사용한다(창작 금지).
 */
class NewStartGalmuriDisplayTest {

  private static final String MACHINE_CASE = """
      ## 분류
      - RESULT_PARTIAL (OPERATOR_PREDICTION은 운영자 예측이며 확정 결과가 아니다)
      ## 결과/상태
      - 확정 감면율: UNKNOWN
      - 임시 감면율: UNKNOWN
      - 예측 감면율(운영자): 0%
      - 결과 추적(author followup): UNKNOWN
      - 예측 vs 실제: UNKNOWN
      ## 상황
      - 채무합계: UNKNOWN
      - 채무분해: UNKNOWN
      - 재산(부동산/차량/금융): 아파트 약 2억 / UNKNOWN / UNKNOWN
      - 소득(2025/현재): UNKNOWN / UNKNOWN
      - 가구/나이/직업상태: 차상위계층 / 60대 / 폐업
      - 신청 유형: 부실차주
      - 운영자 예측 전문: 근저당 설정 시 감면에 유리할 수 있다고 답변
      - 특수상황: 행정복지센터 지원 수급 중
      - 핵심 인사이트: 재산가액이 감면율에 영향
      ## 원문 본문
      아파트가 부동산테크 기준 2억, 실거래가 1.3억입니다. 근저당을 설정하면 감면에 유리한가요?
      """;

  @Test
  @DisplayName("given CASE 머신 직렬화 / when humanize / enum·UNKNOWN 직접 노출 0, 사람용 섹션으로 재조립")
  void machineCaseBecomesHumanReadable() {
    String out = NewStartGalmuriDisplay.humanizeContent(MACHINE_CASE);

    assertThat(out).doesNotContain("RESULT_CONFIRMED").doesNotContain("RESULT_PARTIAL")
        .doesNotContain("OPERATOR_PREDICTION").doesNotContain("UNKNOWN").doesNotContain("## 분류")
        .doesNotContain("## 원문 본문");
    assertThat(out).contains("[상황]").contains("[결과]").contains("[게시 원문]").contains("[주의해서 볼 점]");
    // 없는 정보는 나열하지 않고 자연어로만 남긴다.
    assertThat(out).contains("아직 최종 결과는 확인되지 않았습니다.");
    // 원문이 지지하는 사실만 재배치 — 원문 본문은 그대로 보존.
    assertThat(out).contains("아파트가 부동산테크 기준 2억");
    // 혼재 값은 "미확인"으로만 표시.
    assertThat(out).contains("아파트 약 2억 / 미확인 / 미확인");
    // 운영자 예측은 확정 사실과 구분된다.
    assertThat(out).contains("[운영자 예측 — 확정 결과 아님]").contains("근저당 설정 시 감면에 유리");
    assertThat(out).contains("공식적으로 확인된 규칙이 아니며");
  }

  @Test
  @DisplayName("given 결과가 확정된 사례 / when humanize / 결과 행 표시 + 사례 기준 한정은 유지")
  void confirmedCaseKeepsResultAndCaution() {
    String out = NewStartGalmuriDisplay.humanizeContent("""
        ## 분류
        - RESULT_CONFIRMED (OPERATOR_PREDICTION은 운영자 예측이며 확정 결과가 아니다)
        ## 결과/상태
        - 확정 감면율: 70%
        - 임시 감면율: UNKNOWN
        - 예측 감면율(운영자): UNKNOWN
        - 결과 추적(author followup): 체결 완료
        - 예측 vs 실제: UNKNOWN
        ## 상황
        - 채무합계: 1.2억
        ## 원문 본문
        체결 완료되었습니다.
        """);
    assertThat(out).contains("- 확정 감면율: 70%").contains("- 결과 추적: 체결 완료");
    assertThat(out).doesNotContain("미확인").doesNotContain("아직 최종 결과는 확인되지 않았습니다.");
    assertThat(out).contains("개별 심사는 개인별 사정");
  }

  @Test
  @DisplayName("given 이미 사람이 읽는 텍스트 / when humanize / 통과(원본 불변)")
  void humanTextPassesThrough() {
    String plain = "추심 연락은 1일 수회 제한을 요청할 수 있습니다.";
    assertThat(NewStartGalmuriDisplay.humanizeContent(plain)).isEqualTo(plain);
    assertThat(NewStartGalmuriDisplay.humanizeContent(null)).isNull();
  }

  @Test
  @DisplayName("given 내부 enum 태그 / when humanizeTags / 사람용 badge로 변환, 머신 토큰 미노출")
  void tagsBecomeHumanBadges() {
    assertThat(NewStartGalmuriDisplay.humanizeTags(List.of(
        "CASE", "REVIEW", "RESULT_CONFIRMED", "board:부실차주 신청",
        "OPERATIONAL_CHANGE_SUSPECTED", "OFFICIAL_CHANGE_UNCONFIRMED", "추심", "연락")))
        .containsExactly("실제 사례", "결과 확인", "운영 변화 의심", "공식 확인 필요", "추심", "연락");
  }
}
