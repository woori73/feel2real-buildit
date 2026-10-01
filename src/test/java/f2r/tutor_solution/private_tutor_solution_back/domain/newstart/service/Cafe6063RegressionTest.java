package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiQuestionIntent;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 카페 게시글 #6063 회귀 — 지피지기 STEP 1/2 가 "핵심 걱정 → 운영자 경험 → 행동 순서 → 필요한 공식규정만" 순서로
 * 모델에 근거·지시를 주는지 결정적으로 검증한다(모델 호출 없음, 캡처 transport). fixture 는 {@link Cafe6063Fixture} 주의사항 참고
 * (원문 미확보 부분은 재구성본).
 */
@DisplayName("#6063 회귀 — 불안 질문 우선 + 운영자 경험 우선 + STEP 2 범위 한정")
class Cafe6063RegressionTest {

  private static JsonNode fixtureJson;
  private static Cafe6063Fixture.CapturingTransport step1;
  private static Cafe6063Fixture.CapturingTransport step2;
  private static NewStartAiChatService.NewStartAiAnswer[] answers;

  @BeforeAll
  static void run() throws Exception {
    try (InputStream in = Cafe6063RegressionTest.class.getResourceAsStream("/newstart/regression/cafe_6063.json")) {
      assertThat(in).as("fixture json").isNotNull();
      fixtureJson = new ObjectMapper().readTree(in);
    }
    step1 = new Cafe6063Fixture.CapturingTransport();
    step2 = new Cafe6063Fixture.CapturingTransport();
    answers = Cafe6063Fixture.run(step1, step2);
  }

  @Test
  @DisplayName("fixture 보존: 질문·운영자 답변 요지·개선 전 STEP 2 무관 주제가 JSON 에 있고 재구성 여부가 명시돼 있다")
  void fixtureIsPreserved() {
    assertThat(fixtureJson.get("fixtureId").asText()).isEqualTo("cafe-6063");
    assertThat(fixtureJson.get("question").asText()).isEqualTo(Cafe6063Fixture.QUESTION);
    assertThat(fixtureJson.get("questionIsVerbatim").asBoolean()).isFalse();
    assertThat(fixtureJson.get("operatorAnswerPoints")).hasSize(8);
    assertThat(fixtureJson.get("oldStep2OffTopicRules")).hasSize(7);
  }

  @Test
  @DisplayName("1. 집 압류에 대한 걱정을 핵심 걱정(불안 질문)으로 인식하고 STEP 1 첫 부분의 목적을 '위험도 판단'으로 지시한다")
  void recognizesCoreConcern() {
    JipijigiQuestionIntent intent = JipijigiQuestionIntent.analyze(Cafe6063Fixture.QUESTION);
    assertThat(intent.anxious()).isTrue();
    assertThat(intent.concerns()).anyMatch(c -> c.contains("추심·압류") && c.contains("집"));
    assertThat(step1.system).contains("질문자의 큰 걱정").contains("불안 표현이 있는 질문이다")
        .contains("규정 설명이 아니라 '지금 무엇을 걱정해야 하고 무엇은 아직 걱정하지 않아도 되는지'");
  }

  @Test
  @DisplayName("2. 운영자 직접 경험이 전자책보다 앞서 근거 초반에 오고 provenance 가 '운영자 직접 경험'으로 구분된다")
  void operatorExperienceComesFirst() {
    String ctx = step1.system.substring(step1.system.indexOf("[NewStart 지식베이스 근거]\n(전자책"));
    assertThat(ctx.indexOf("자료 성격: 운영자 직접 경험")).isNotNegative().isLessThan(ctx.indexOf("자료 성격: 전자책"));
    List<Evidence> ordered = NewStartAiChatService.caseEvidence(Cafe6063Fixture.pack(),
        JipijigiQuestionIntent.analyze(Cafe6063Fixture.QUESTION));
    assertThat(ordered.subList(0, 3)).allMatch(NewStartAiChatService::isOperatorExperience);
    assertThat(ordered.get(3).getNote()).contains("sourceType=EBOOK");
  }

  @Test
  @DisplayName("3. 원장초기화 약 3일 경험이 출처(운영자 직접 경험·시점)와 함께 근거에 있고 소요일을 지어내지 말라는 지시가 있다")
  void ledgerResetThreeDaysWithProvenance() {
    String ctx = step1.system;
    int item = ctx.indexOf("[운영자 경험] 원장초기화 후 Credit4U");
    assertThat(item).isNotNegative();
    String block = ctx.substring(item, ctx.indexOf("\n3. ", item) > 0 ? ctx.indexOf("\n3. ", item) : ctx.length());
    assertThat(block).contains("자료 성격: 운영자 직접 경험").contains("시점: 2026-09-20").contains("보통 약 3일");
    assertThat(NewStartAiChatService.CASE_STEP_PROMPT).contains("운영자 경험상 보통 약 N일").contains("다른 숫자를 만들지 않는다");
  }

  @Test
  @DisplayName("4. Credit4U 확인 → 부실차주 신청 순서와 시간 순서 행동 제시 지시가 있다")
  void actionOrderIsChronological() {
    String ctx = step1.system;
    int credit4u = ctx.indexOf("Credit4U에서 장기연체 등록을 확인한 다음 부실차주를 신청");
    assertThat(credit4u).isNotNegative();
    assertThat(NewStartAiChatService.CASE_STEP_PROMPT).contains("지금 해야 할 행동을 시간 순서대로 번호로 제시한다");
  }

  @Test
  @DisplayName("5. 대부업체 협약/비협약 구분(협약기관 확인 → 비협약은 부실차주 신청 후 신복위 개인워크아웃)이 STEP 1 근거에 있다")
  void lenderCoverageSplit() {
    assertThat(step1.system).contains("협약기관인지 먼저 확인").contains("비협약기관 채무는 부실차주 신청 후 신복위 개인워크아웃");
  }

  @Test
  @DisplayName("6. 추심·압류 문제가 생기면 금감원 민원 대응을 제시하도록 지시·근거가 있다(NewStart 민원 도우미 포함)")
  void collectionResponsePath() {
    assertThat(step1.system).contains("실제 추심·압류 문제가 생기면 금감원 민원으로 대응");
    assertThat(NewStartAiChatService.CASE_STEP_PROMPT).contains("금감원 민원 대응").contains("추심 대응 민원 도우미");
    assertThat(step1.system).contains("/newstart/fss-helper");
  }

  @Test
  @DisplayName("7. STEP 2 는 질문과 무관한 공식 규정(공공정보 해제·조기상환·가상자산·비상장주식·고소득자·사해행위·변제가능률)을 싣지 않고 직접 관련된 것만 쓴다")
  void step2DropsOffTopicOfficialRules() {
    for (JsonNode off : fixtureJson.get("oldStep2OffTopicRules")) {
      assertThat(step2.system).as("무관 공식 주제 %s", off.asText()).doesNotContain(off.asText());
    }
    assertThat(step2.system).contains("협약기관 채무 매입").contains("불법·부당 채권추심 피해 민원 안내");
    assertThat(answers[1].sources()).hasSize(2);
    assertThat(step2.system).contains("[확인 범위").contains("직접 관련 없는 공식 규정은 근거에 있어도 쓰지 않는다")
        .contains("항목은 5개 이내");
  }

  @Test
  @DisplayName("8. STEP 2 는 STEP 1 을 덮어쓰지 않는다(1차 답변은 비교 입력일 뿐, 반복·삭제·정정 금지)")
  void step2DoesNotOverwriteStep1() {
    assertThat(step2.prompt).contains("다시 쓰지 말 것");
    assertThat(NewStartAiChatService.OFFICIAL_STEP_PROMPT).contains("반복하거나 삭제·정정하지 말고").contains("1차 답변을 덮어쓰지 않는다");
    assertThat(step2.system).doesNotContain("[운영자·전자책 자료]").doesNotContain("[부실차주 카페 사례]");
  }

  @Test
  @DisplayName("9. 운영자 경험과 공식규정은 서로 다른 provenance 로 유지된다(경험은 STEP 1, 공식은 STEP 2, 충돌 시 나란히 표시)")
  void provenanceSeparated() {
    assertThat(step1.system).doesNotContain("[공식 자료]");
    assertThat(answers[0].sources()).extracting(NewStartAiChatService.AnswerSource::kind).doesNotContain("공식 자료");
    assertThat(answers[1].sources()).extracting(NewStartAiChatService.AnswerSource::kind).containsOnly("공식 자료");
    assertThat(NewStartAiChatService.OFFICIAL_STEP_PROMPT).contains("운영자 경험은 이 사례 기준, 공식 안내는 다르게 안내한다")
        .contains("공식 규정으로 바꿔 쓰거나");
  }

  @Test
  @DisplayName("10. 운영자 판단을 법률 규정으로 바꾸지 않는다 — '집 압류 걱정 없다'는 운영자 판단이지 법적 보장이 아님을 STEP 1 이 명시한다")
  void operatorJudgmentIsNotLegalGuarantee() {
    assertThat(NewStartAiChatService.CASE_STEP_PROMPT)
        .contains("법적으로 항상 그렇다는 뜻이 아니다").contains("법률 규정이나 보장으로 바꿔 말하지 않고")
        .contains("'걱정하지 마세요'·'힘내세요' 같은 근거 없는 위로는 쓰지 않고");
  }

  @Test
  @DisplayName("회귀 방지: 특정 사례 하드코딩이 아니다 — 신호 없는 일반 질문은 분석 블록 없이 공식자료를 기존 방식(최대 8건)으로 쓴다")
  void noSignalQuestionIsUnchanged() {
    JipijigiQuestionIntent none = JipijigiQuestionIntent.analyze("새출발기금이 무엇인가요?");
    assertThat(none.hasSignal()).isFalse();
    assertThat(none.promptBlock()).isEmpty();
    List<Evidence> official = new ArrayList<>(NewStartAiChatService.officialEvidence(Cafe6063Fixture.pack(), none));
    assertThat(official).hasSize(8);
    assertThat(official).extracting(Evidence::getSourceClass).containsOnly(SourceClass.OFFICIAL_RULE);
  }
}
