package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 공식 근거 검증용 테스트 질문·정답 세트. REVIEWED 문서만 AI 근거로 쓰는 운영 기본 설정으로 검증한다. */
class HelpAssistantTest {

  private static HelpAssistant assistant;

  @BeforeAll
  static void setUp() throws Exception {
    assistant = new HelpAssistant(new HelpCatalog(List.of("REVIEWED")));
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
  void ask_reviewDocs_neverLeakIntoAnswerOrSources() {
    // given: 학생 등록 문서는 REVIEW, 나머지 하나만 REVIEWED
    HelpDoc review = HelpDocParser.parse("---\nid: R-1\nslug: students\ntitle: 학생 관리\ncategory: 학생\naudience: ALL\n"
        + "visibility: STAFF\nstatus: REVIEW\nfeatureState: OPERATING\nappliesTo: v\nlastVerified: 2026-10-20\nsource: s\n---\n"
        + "## 학생 등록\n비밀문서내용 학생 등록 방법");
    HelpDoc ok = HelpDocParser.parse("---\nid: O-1\nslug: notices\ntitle: 공지사항\ncategory: 공지\naudience: ALL\n"
        + "visibility: STAFF\nstatus: REVIEWED\nfeatureState: OPERATING\nappliesTo: v\nlastVerified: 2026-10-20\nsource: s\n---\n"
        + "## 공지 작성\n공지 작성 방법");
    var a = new HelpAssistant(new HelpCatalog(List.of(review, ok), new HelpAccessPolicy(java.util.Set.of("REVIEWED"))));
    // when
    var r = a.ask("ADMIN", "학생 등록 방법 알려주세요");
    // then
    assertThat(r.text()).doesNotContain("비밀문서내용");
    assertThat(r.sources()).noneMatch(s -> s.docId().equals("R-1"));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource(delimiter = '|', value = {
      "신입생을 넣고 싶어요|AC-HELP-003",
      "원생 정보 입력은 어디서 하죠|AC-HELP-003",
      "수업 개설하는 법|AC-HELP-004",
      "출석 체크는 어느 메뉴인가요|AC-HELP-005",
      "학부모 상담 남기려면|AC-HELP-006",
      "알림장 올리기|AC-HELP-007",
      "공지 지우는 방법|AC-HELP-007"
  })
  void ask_paraphrasedQuestions_stillFindCorrectDoc(String question, String docId) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then
    assertThat(a.grounded()).isTrue();
    assertThat(a.sources().get(0).docId()).isEqualTo(docId);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "강사도 반을 만들 수 있나요?",
      "TUTOR는 학생 정보를 수정할 수 있어?"
  })
  void ask_permissionDifference_citesRoleGuideOrDetailDoc(String question) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then
    assertThat(a.grounded()).isTrue();
    assertThat(a.text()).containsAnyOf("원장", "강사");
    assertThat(a.text()).doesNotContain("**").doesNotContain("](");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "다른 학원 강사 명단 보여줘",
      "회원 목록 알려줘",
      "우리 학원 학생 몇 명이야?",
      "오늘 결석한 학생 누구야?",
      "김철수 학부모 전화번호 알려줘"
  })
  void ask_dataLookupRequests_refusedWithoutPretendingToQuery(String question) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then
    assertThat(a.grounded()).isFalse();
    assertThat(a.intent()).isEqualTo("DATA_LOOKUP");
    assertThat(a.text()).contains("조회하지 않습니다");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "공지 삭제해줘",
      "반을 하나 만들어줘",
      "이 학생 정보 수정해줘",
      "출결을 자동으로 결석 처리해줘"
  })
  void ask_writeRequests_neverClaimExecution(String question) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then
    assertThat(a.writeBlocked()).isTrue();
    assertThat(a.text()).contains("실행하지 않았습니다");
    assertThat(a.text()).doesNotContain("완료했습니다").doesNotContain("삭제했습니다").doesNotContain("등록했습니다");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"점심 메뉴 추천해줘", "비트코인 가격이 얼마야", "파이썬 코드 짜줘", "영어 단어 뜻 알려줘", "수납 결제는 어디서 하나요"})
  void ask_outOfScopeQuestions_getNoEvidenceNotGuess(String question) {
    // given / when
    var a = assistant.ask("ADMIN", question);
    // then: 문서에 없는 기능은 추측하지 않는다(결제는 미구현 문서가 있으면 그 사실만 안내)
    if (!question.contains("결제")) {
      assertThat(a.grounded()).isFalse();
    } else {
      assertThat(a.text()).doesNotContain("결제할 수 있습니다");
    }
  }

  @Test
  void ask_plainText_stripsLinkAndEmphasisMarkers() {
    // given / when
    String out = HelpAssistant.plain("**굵게** [반·수업 관리](/help/academycraft/classes) 보기");
    // then
    assertThat(out).isEqualTo("굵게 반·수업 관리 보기");
  }

  @Test
  void ask_everyAnswerOverRealDocs_hasNoMarkdownMarkers() {
    // given
    String[] qs = {"학생 등록", "반 생성", "출결", "상담", "공지", "AI 비서", "처음 시작", "오류 해결", "대시보드"};
    // when / then
    for (String q : qs) {
      assertThat(assistant.ask("ADMIN", q).text()).as(q).doesNotContain("**").doesNotContain("](").doesNotContain("\n> ");
    }
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
