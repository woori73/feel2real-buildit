package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("입시상담 답변 — 한글로 풀어 쓴 등급 합을 숫자로")
class AdmissionsAnswerDigitsTest {

    @Test
    @DisplayName("given '등급 합이 오 이내' when 후처리하면 then '등급 합이 5 이내'로 바뀐다")
    void spelled_grade_sum_becomes_digit() {
        // given: 2026-11-24 운영 ev-min-011 답
        String answer = "국어, 수학, 영어, 탐구 중 세 개 영역 등급 합이 오 이내입니다.";

        // when
        String fixed = AdmissionsChatService.digitsForSpelledGrades(answer);

        // then: 등급 합만 바꾸고 '세 개 영역'은 그대로 둔다
        assertThat(fixed).isEqualTo("국어, 수학, 영어, 탐구 중 세 개 영역 등급 합이 5 이내입니다.");
    }

    @Test
    @DisplayName("given '합 십일 이내'·'합은 칠 이하' when 후처리하면 then 두 자리·이하도 숫자로 바뀐다")
    void two_digit_and_iha_forms() {
        // given
        String answer = "간호학과는 2개 영역 합 십일 이내, 의예과는 합은 칠 이하입니다.";

        // when / then
        assertThat(AdmissionsChatService.digitsForSpelledGrades(answer)).isEqualTo("간호학과는 2개 영역 합 11 이내, 의예과는 합은 7 이하입니다.");
    }

    @Test
    @DisplayName("given 숫자가 이미 아라비아 숫자이거나 다른 한글 단어 when 후처리하면 then 바뀌지 않는다")
    void unrelated_text_unchanged() {
        // given
        String answer = "3개 영역 등급 합 6 이내입니다. 합이 이렇게 정해졌고 오후에 발표합니다.";

        // when / then
        assertThat(AdmissionsChatService.digitsForSpelledGrades(answer)).isEqualTo(answer);
        assertThat(AdmissionsChatService.digitsForSpelledGrades(null)).isNull();
    }
}
