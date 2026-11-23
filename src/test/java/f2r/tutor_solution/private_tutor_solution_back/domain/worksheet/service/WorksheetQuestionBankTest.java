package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository.TpQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.SetInfo;
import java.util.List;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("WorksheetQuestionBank — 내신 예상문제·예비고1 어법을 한 모양의 문항으로 읽기")
class WorksheetQuestionBankTest {

  TpExamRepository exams;
  TpQuestionRepository questions;
  WorksheetQuestionBank bank;

  WikiCsatGateway wiki;
  static final AuthenticatedPrincipal OWNER = new AuthenticatedPrincipal(1L, "owner", "ADMIN");

  @BeforeEach
  void setUp() throws Exception {
    exams = mock(TpExamRepository.class);
    questions = mock(TpQuestionRepository.class);
    ObjectMapper json = new ObjectMapper();
    wiki = mock(WikiCsatGateway.class);
    bank = new WorksheetQuestionBank(exams, questions, new PreHighGrammarBank(json), json, wiki);
    TpExam exam = BeanUtils.instantiateClass(TpExam.class.getDeclaredConstructor());
    exam.setExamCode("TP-NAESIN-APM2-2602-R01");
    exam.setTitle("압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회");
    ReflectionTestUtils.setField(exam, "examId", 7L);
    when(exams.findByKindAndStatusOrderByExamIdAsc("NAESIN", "PUBLISHED")).thenReturn(List.of(exam));
    List<TpQuestion> rows = List.of(
        question(101L, 1, 2, "정답 ② · 어법상 어색한 것은? (2점)\n해설", "B"),
        question(102L, 2, 4, "정답 ①, ③ · 내용 일치 (4점)\n해설", "A,C"));
    when(questions.countByExamId(7L)).thenReturn(2L);
    when(questions.findByExamIdInOrderByExamIdAscOrderNoAsc(any())).thenReturn(rows);
    when(questions.findAllById(any())).thenReturn(rows);
  }

  private static TpQuestion question(Long id, int no, int points, String explanation, String answer) throws Exception {
    TpQuestion q = BeanUtils.instantiateClass(TpQuestion.class.getDeclaredConstructor());
    ReflectionTestUtils.setField(q, "questionId", id);
    q.setExamId(7L);
    q.setOrderNo(no);
    q.setPoints(points);
    q.setPrompt("[[img:/naesin/apgujeong-m2-2026-2/r01/q0" + no + ".webp]]");
    q.setChoicesJson("[\"①\", \"②\", \"③\", \"④\", \"⑤\"]");
    q.setAnswerKey(answer);
    q.setExplanation(explanation);
    return q;
  }

  @Test
  @DisplayName("given 내신 1회차와 어법 자료 when sets then 내신은 학교·학년으로, 어법은 단원 1·2부 20개 + 마무리 10회로 묶인다")
  void sets_listsNaesinAndGrammar() {
    // when
    List<SetInfo> sets = bank.sets();

    // then
    assertThat(sets).hasSize(31);
    assertThat(sets.get(0)).isEqualTo(new SetInfo("NAESIN:TP-NAESIN-APM2-2602-R01", "NAESIN", "압구정중 2학년",
        "압구정중 2학년 2학기 기말 1회 · Lesson 7 예상문제 1회", 2));
    assertThat(sets).filteredOn(s -> s.source().equals("GRAMMAR")).hasSize(30)
        .allSatisfy(s -> assertThat(s.questionCount()).isEqualTo(20));
  }

  @Test
  @DisplayName("given 내신 묶음 when search then 발문으로 유형, 배점으로 난이도, 정답은 동그라미 번호로 읽는다")
  void search_mapsNaesinQuestion() {
    // when
    List<Question> found = bank.search(List.of("NAESIN:TP-NAESIN-APM2-2602-R01"), null, null);

    // then
    assertThat(found).extracting(Question::ref).containsExactly("NAESIN:101", "NAESIN:102");
    assertThat(found.get(0).type()).isEqualTo("어법");
    assertThat(found.get(0).level()).isEqualTo("하");
    assertThat(found.get(0).answer()).isEqualTo("②");
    assertThat(found.get(1).type()).isEqualTo("독해");
    assertThat(found.get(1).level()).isEqualTo("상");
    assertThat(found.get(1).answer()).isEqualTo("①, ③");
    assertThat(WorksheetQuestionBank.countByType(found)).containsEntry("어법", 1L).containsEntry("독해", 1L);
  }

  @Test
  @DisplayName("given 유형·난이도 조건 when search then 맞는 문항만 남고, 난이도 없는 어법 문항은 난이도 조건에서 빠진다")
  void search_filtersByTypeAndLevel() {
    // given
    List<String> sets = List.of("NAESIN:TP-NAESIN-APM2-2602-R01", "GRAMMAR:1-1");

    // when & then
    assertThat(bank.search(sets, List.of("독해"), null)).extracting(Question::ref).containsExactly("NAESIN:102");
    assertThat(bank.search(sets, null, List.of("하"))).extracting(Question::ref).containsExactly("NAESIN:101");
    assertThat(bank.search(sets, List.of("어법"), null)).hasSize(21);
    assertThat(bank.search(List.of(), null, null)).isEmpty();
  }

  @Test
  @DisplayName("given 문항 주소 목록 when resolve then 준 순서대로 돌려주고 없는 문항·잘못된 주소는 빠진다")
  void resolve_keepsOrderAndDropsMissing() {
    // when
    List<Question> found = bank.resolve(List.of("GRAMMAR:1-1:3", "NAESIN:102", "NAESIN:999", "GRAMMAR:1-1:21",
        "GRAMMAR:nope:1", "NAESIN:abc", "NAESIN:101"));

    // then
    assertThat(found).extracting(Question::ref).containsExactly("GRAMMAR:1-1:3", "NAESIN:102", "NAESIN:101");
    assertThat(found.get(0).source()).isEqualTo("GRAMMAR");
    assertThat(found.get(0).choices()).hasSize(5);
    assertThat(found.get(0).level()).isNull();
  }

  @Test
  @DisplayName("given 시험 제목 when groupOf then '○○중 N학년'까지를 묶음 이름으로, 형식이 다르면 '내신 예상문제'")
  void groupOf_readsSchoolAndGrade() {
    // given & when & then
    assertThat(WorksheetQuestionBank.groupOf("신구중 2학년 2학기 기말 3회")).isEqualTo("신구중 2학년");
    assertThat(WorksheetQuestionBank.groupOf("기말 대비")).isEqualTo("내신 예상문제");
    assertThat(WorksheetQuestionBank.circled("E")).isEqualTo("⑤");
    assertThat(WorksheetQuestionBank.circled(null)).isEmpty();
  }

  private TpExam tpExam(long id, String kind, String code, String title) throws Exception {
    TpExam e = BeanUtils.instantiateClass(TpExam.class.getDeclaredConstructor());
    e.setKind(kind);
    e.setExamCode(code);
    e.setTitle(title);
    ReflectionTestUtils.setField(e, "examId", id);
    return e;
  }

  private static TpQuestion tpQuestion(long id, long examId, int no, String type, String audio, String answer, String explanation)
      throws Exception {
    TpQuestion q = BeanUtils.instantiateClass(TpQuestion.class.getDeclaredConstructor());
    ReflectionTestUtils.setField(q, "questionId", id);
    q.setExamId(examId);
    q.setOrderNo(no);
    q.setTaskType(type);
    q.setAudioMode(audio);
    q.setPrompt("The staff responded ------- to every request.");
    q.setBodyText(type.equals("TOEIC_P7") ? "Questions 147-148 refer to the following notice." : null);
    q.setChoicesJson("[\"prompt\", \"promptly\", \"promptness\", \"prompted\"]");
    q.setAnswerKey(answer);
    q.setExplanation(explanation);
    return q;
  }

  @Test
  @DisplayName("given TOEIC 세트(Part 2 듣기·Part 5·Part 7) when sets·search then 종이에 풀 수 있는 Part 5·7만 담고 정답은 동그라미, 유형은 화면 이름")
  void testprep_onlyPaperFriendly() throws Exception {
    // given
    TpExam toeic = tpExam(30L, "TOEIC", "TP-TOEIC-RC-01", "TOEIC Reading Practice 01");
    when(exams.findByKindAndStatusOrderByExamIdAsc("TOEIC", "PUBLISHED")).thenReturn(List.of(toeic));
    List<TpQuestion> rows = List.of(tpQuestion(301L, 30L, 1, "TOEIC_P2", "TTS", "A", "듣기"),
        tpQuestion(302L, 30L, 2, "TOEIC_P5", "NONE", "B", "[문법-품사(부사 자리)] 정답 (B)."),
        tpQuestion(303L, 30L, 3, "TOEIC_P7", "NONE", "D", "[세부 사항] 정답 (D)."),
        tpQuestion(304L, 30L, 4, "WRITE_EMAIL", "NONE", null, "수동 채점"));
    when(questions.findByExamIdOrderByOrderNoAsc(30L)).thenReturn(rows);
    when(questions.findAllById(any())).thenReturn(rows);

    // when
    List<SetInfo> sets = bank.sets();
    List<Question> found = bank.search(List.of("TOEIC:TP-TOEIC-RC-01"), null, null);
    List<Question> resolved = bank.resolve(List.of("TOEIC:303", "TOEIC:301", "TOEIC:302"));

    // then
    assertThat(sets).filteredOn(s -> s.source().equals("TOEIC")).singleElement()
        .satisfies(s -> assertThat(s).isEqualTo(new SetInfo("TOEIC:TP-TOEIC-RC-01", "TOEIC", "TOEIC 독해 연습", "TOEIC Reading Practice 01", 2)));
    assertThat(found).extracting(Question::ref).containsExactly("TOEIC:302", "TOEIC:303");
    assertThat(found.get(0).type()).isEqualTo("Part 5 단문 빈칸");
    assertThat(found.get(0).topic()).isEqualTo("문법-품사(부사 자리)");
    assertThat(found.get(0).answer()).isEqualTo("②");
    assertThat(found.get(0).choices()).containsExactly("prompt", "promptly", "promptness", "prompted");
    assertThat(found.get(1).body()).contains("Questions 147-148");
    assertThat(resolved).extracting(Question::ref).containsExactly("TOEIC:303", "TOEIC:302");
  }

  @Test
  @DisplayName("given iBT 모의고사·어휘 퀴즈 when sets then iBT 한 출처 안에 두 묶음 이름으로 나뉜다")
  void ibt_groupsPracticeAndVocab() throws Exception {
    // given
    when(exams.findByKindAndStatusOrderByExamIdAsc("PRACTICE", "PUBLISHED"))
        .thenReturn(List.of(tpExam(40L, "PRACTICE", "TP-PRACTICE-2026-MINI", "TOEFL iBT Practice — Mini")));
    when(exams.findByKindAndStatusOrderByExamIdAsc("VOCAB", "PUBLISHED"))
        .thenReturn(List.of(tpExam(41L, "VOCAB", "TP-VOCAB-EASY", "Vocabulary Quiz — Easy")));
    when(questions.findByExamIdOrderByOrderNoAsc(40L)).thenReturn(List.of(tpQuestion(401L, 40L, 1, "READ_PASSAGE", "NONE", "B", "")));
    when(questions.findByExamIdOrderByOrderNoAsc(41L)).thenReturn(List.of(tpQuestion(411L, 41L, 1, "VOCAB_CLOZE", "NONE", "C", "")));

    // when
    List<SetInfo> ibt = bank.sets().stream().filter(s -> s.source().equals("IBT")).toList();

    // then
    assertThat(ibt).extracting(SetInfo::group).containsExactly("iBT 모의고사", "iBT 어휘 퀴즈");
  }

  @Test
  @DisplayName("given 위키 수능 시험 when 로그인한 사람으로 sets·search·resolve then 학년도별 묶음, 정답 동그라미, 주소 CSAT:시험-번호, 로그인 정보가 없으면 위키를 부르지 않는다")
  void csat_fromWiki() {
    // given
    WikiCsatGateway.Exam exam = new WikiCsatGateway.Exam(5L, 2026, "CSAT", "", 2);
    when(wiki.exams(OWNER)).thenReturn(List.of(exam, new WikiCsatGateway.Exam(6L, 2026, "JUNE_MOCK", "", 0)));
    when(wiki.questions(OWNER, 5L)).thenReturn(List.of(
        new WikiCsatGateway.Item(18, "글의 목적", "Dear residents, ...", "다음 글의 목적으로 가장 적절한 것은?",
            List.of("a", "b", "c", "d", "e"), "3", "해설", ""),
        new WikiCsatGateway.Item(41, "제목", "Long passage", "윗글의 제목으로 가장 적절한 것은?", List.of("a", "b", "c", "d", "e"),
            "②", "", "[41~42]")));

    // when
    List<SetInfo> sets = bank.sets(OWNER);
    List<Question> found = bank.search(OWNER, List.of("CSAT:5"), List.of("제목"), null);
    List<Question> resolved = bank.resolve(OWNER, List.of("CSAT:5-41", "CSAT:5-18", "CSAT:5-99"));

    // then
    assertThat(sets).filteredOn(s -> s.source().equals("CSAT")).singleElement()
        .satisfies(s -> assertThat(s).isEqualTo(new SetInfo("CSAT:5", "CSAT", "2026학년도", "2026학년도 대학수학능력시험 영어", 2)));
    assertThat(found).singleElement().satisfies(q -> {
      assertThat(q.ref()).isEqualTo("CSAT:5-41");
      assertThat(q.answer()).isEqualTo("②");
      assertThat(q.topic()).isEqualTo("[41~42]");
      assertThat(q.body()).isEqualTo("Long passage");
    });
    assertThat(resolved).extracting(Question::ref).containsExactly("CSAT:5-41", "CSAT:5-18");
    assertThat(resolved.get(1).answer()).isEqualTo("③");
    assertThat(bank.sets()).noneMatch(s -> s.source().equals("CSAT"));
    org.mockito.Mockito.verify(wiki, org.mockito.Mockito.never()).exams(null);
  }

  @Test
  @DisplayName("given 위키 정답 여러 모양 when csatAnswer then 숫자·번호·동그라미는 동그라미로, 그 밖은 그대로")
  void csatAnswer_normalizes() {
    // given & when & then
    assertThat(WorksheetQuestionBank.csatAnswer("3")).isEqualTo("③");
    assertThat(WorksheetQuestionBank.csatAnswer(" 5번 ")).isEqualTo("⑤");
    assertThat(WorksheetQuestionBank.csatAnswer("①")).isEqualTo("①");
    assertThat(WorksheetQuestionBank.csatAnswer("서술형")).isEqualTo("서술형");
    assertThat(WorksheetQuestionBank.csatAnswer(null)).isEmpty();
  }
}
