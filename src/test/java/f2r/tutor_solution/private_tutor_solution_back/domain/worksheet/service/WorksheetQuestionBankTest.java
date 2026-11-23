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

  @BeforeEach
  void setUp() throws Exception {
    exams = mock(TpExamRepository.class);
    questions = mock(TpQuestionRepository.class);
    ObjectMapper json = new ObjectMapper();
    bank = new WorksheetQuestionBank(exams, questions, new PreHighGrammarBank(json), json);
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
}
