package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JipijigiQuestionIntent — 핵심 걱정·진행 단계·검색 확장·관련도(규칙 기반, LLM 없음)")
class JipijigiQuestionIntentTest {

  @Test
  @DisplayName("given 불안 표현 when analyze then anxious=true, 압류·추심 걱정과 분석 블록이 만들어진다")
  void anxiousQuestion() {
    var i = JipijigiQuestionIntent.analyze("추심이나 압류가 들어와서 집이 걱정됩니다. 어떻게 해야 할지 막막합니다.");

    assertThat(i.anxious()).isTrue();
    assertThat(i.concerns()).hasSize(1);
    assertThat(i.promptBlock()).contains("불안 표현이 있는 질문이다").contains("추심·압류");
    assertThat(i.expansions()).contains("추심", "압류", "금감원", "민원");
  }

  @Test
  @DisplayName("given 원장초기화 질문 when analyze then 원장정리·Credit4U 장기연체·부실차주로 검색이 확장된다")
  void ledgerResetExpandsRetrieval() {
    var i = JipijigiQuestionIntent.analyze("회생 폐지 후 원장 초기화는 얼마나 걸리나요?");

    assertThat(i.anxious()).isFalse();
    assertThat(i.expansions()).contains("원장초기화", "원장정리", "회생 폐지", "장기연체", "Credit4U", "부실차주");
    assertThat(i.stages()).anyMatch(s -> s.contains("원장정리"));
    assertThat(JipijigiResearchService.withIntentKeywords(List.of("회생", "폐지"), i))
        .contains("회생", "폐지", "원장초기화", "Credit4U")
        .hasSizeLessThanOrEqualTo(JipijigiResearchService.MAX_KEYWORDS_WITH_INTENT);
  }

  @Test
  @DisplayName("given 신호 없는 질문 when analyze then 아무 것도 붙이지 않는다(무관한 키워드·규정을 '안전을 위해' 덧붙이지 않음)")
  void noSignal() {
    var i = JipijigiQuestionIntent.analyze("차량담보대출이 있는데 새출발기금 신청하면 차를 계속 탈 수 있나?");

    assertThat(i.hasSignal()).isFalse();
    assertThat(i.expansions()).isEmpty();
    assertThat(i.promptBlock()).isEmpty();
    assertThat(JipijigiResearchService.withIntentKeywords(List.of("차량", "담보대출"), i)).containsExactly("차량", "담보대출");
  }

  @Test
  @DisplayName("given null·빈 질문 when analyze then none")
  void blank() {
    assertThat(JipijigiQuestionIntent.analyze(null).hasSignal()).isFalse();
    assertThat(JipijigiQuestionIntent.analyze("  ").focusTerms()).isEmpty();
  }

  @Test
  @DisplayName("관련도: 도메인 일반어만 겹치는 자료는 0, 질문 용어가 겹치면 양수(어간 일치 포함)")
  void relevance() {
    var i = JipijigiQuestionIntent.analyze("후순위 담보대출 기한이익상실 통보를 받았어요");

    assertThat(i.relevance("[새출발기금] 가상자산 보유 시 채무조정 기준", "가상자산은 재산으로 반영")).isZero();
    assertThat(i.relevance("[새출발기금] 부실우려 담보채무 안내", "기준")).isPositive();
  }

  @Test
  @DisplayName("운영자 직접 경험 가산 — 전자책보다 앞서되 키워드가 안 맞는 자료는 후보가 아니다")
  void operatorBonusValue() {
    assertThat(JipijigiResearchService.OPERATOR_EXPERIENCE_BONUS).isPositive();
  }
}
