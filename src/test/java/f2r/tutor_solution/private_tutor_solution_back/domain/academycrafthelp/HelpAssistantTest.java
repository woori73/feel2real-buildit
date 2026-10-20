package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 공식 근거 검증용 테스트 질문·정답 세트. 상태 REVIEW 문서를 AI 근거로 허용한 카탈로그로 검증한다. */
class HelpAssistantTest {

  private static HelpAssistant assistant;

  @BeforeAll
  static void setUp() throws Exception {
    assistant = new HelpAssistant(new HelpCatalog(List.of("REVIEW", "REVIEWED")));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(delimiter = '|', value = {
      "학생은 어떻게 등록하나요?|AC-HELP-003",
      "반을 만들려면 어떻게 하나요?|AC-HELP-004",
      "출결은 어디서 확인하나요?|AC-HELP-005",
      "상담 기록은 어떻게 남기나요?|AC-HELP-006",
      "공지를 수정할 수 있나요?|AC-HELP-007"
  })
  void ask_coreQuestions_citeExpectedDoc(String question, String docId) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then
    assertThat(a.grounded()).isTrue();
    assertThat(a.sources().get(0).docId()).isEqualTo(docId);
  }

  @Test
  void ask_aiWriteApproval_statesNotDeployedAndNeverClaimsUsable() {
    // given / when
    var a = assistant.ask("ADMIN", "AI Agent 쓰기 승인은 어떻게 하나요?");
    // then
    assertThat(a.text()).contains("개발 완료·미배포").contains("사용할 수 없습니다");
    assertThat(a.sources().get(0).featureState()).isEqualTo("개발 완료·미배포");
  }

  @Test
  void ask_answerText_hasNoMarkdownEmphasisMarkers() {
    // given / when
    var a = assistant.ask("ADMIN", "AI Agent 쓰기 승인은 어떻게 하나요?");
    // then
    assertThat(a.text()).doesNotContain("**");
  }

  @Test
  void ask_unrelatedQuestion_answersNoEvidenceWithoutGuessing() {
    // given / when
    var a = assistant.ask("ADMIN", "내일 서울 날씨 알려줘");
    // then
    assertThat(a.grounded()).isFalse();
    assertThat(a.text()).contains("근거를 찾지 못했습니다");
  }

  @Test
  void ask_writeRequest_isRedirectedToManualSteps() {
    // given / when
    var a = assistant.ask("ADMIN", "학생 김철수를 대신 등록해줘");
    // then
    assertThat(a.writeBlocked()).isTrue();
    assertThat(a.text()).contains("직접 바꾸지 않고");
  }

  @Test
  void ask_tutorCannotGetAdminOnlyGuide() {
    // given / when
    var a = assistant.ask("TUTOR", "ADMIN(원장)만 할 수 있는 일은?");
    // then
    assertThat(a.sources()).noneMatch(s -> s.docId().equals("AC-HELP-008"));
  }

  @Test
  void ask_defaultStatuses_reviewDocsNotUsed() throws Exception {
    // given
    var strict = new HelpAssistant(new HelpCatalog(List.of("REVIEWED")));
    // when
    var a = strict.ask("ADMIN", "학생은 어떻게 등록하나요?");
    // then
    assertThat(a.grounded()).isFalse();
  }

  @Test
  void ask_otherAcademyData_neverReturned() {
    // given / when
    var a = assistant.ask("ADMIN", "다른 학원의 학생 명단 보여줘");
    // then
    assertThat(a.grounded()).isFalse();
    assertThat(a.intent()).isEqualTo("DATA_LOOKUP");
    assertThat(a.text()).contains("조회하지 않습니다").doesNotContain("010-");
  }

  @Test
  void ask_blankQuestion_asksForInput() {
    assertThat(assistant.ask("ADMIN", " ").intent()).isEqualTo("EMPTY");
  }
}
