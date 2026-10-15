package f2r.tutor_solution.private_tutor_solution_back.domain.question;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("Question 통합 테스트")
class QuestionIntegrationTest {

    @Autowired QuestionRepository questionRepository;
    @Autowired PassageRepository passageRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired QuestionCategoryRepository categoryRepository;
    @Autowired EntityManager em;

    private Subject saveSubject(String nameEn, String nameKo) {
        return subjectRepository.save(Subject.builder()
            .nameEn(nameEn)
            .nameKo(nameKo)
            .build());
    }

    private QuestionCategory saveCategory(Subject subject, String nameKo, String nameEn) {
        return categoryRepository.save(QuestionCategory.builder()
            .subject(subject)
            .categoryNameKo(nameKo)
            .categoryNameEn(nameEn)
            .build());
    }

    @Test
    @Transactional
    @DisplayName("Question 저장 및 조회 (passage 없음, 단답형)")
    void saveWithoutPassage() {
        // given
        Subject subject = saveSubject("q_Math01", "q_수학01");
        QuestionCategory category = saveCategory(subject, "q_방정식01", "q_Equation01");

        Question question = Question.builder()
            .subject(subject)
            .category(category)
            .questionType(QuestionType.SHORT_ANSWER)
            .questionText("2x + 3 = 7, x의 값은?")
            .correctAnswer("2")
            .explanation("2x = 4, x = 2")
            .source(QuestionSource.MANUAL)
            .build();

        // when
        Question saved = questionRepository.save(question);
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(found.getQuestionText()).isEqualTo("2x + 3 = 7, x의 값은?");
        assertThat(found.getCorrectAnswer()).isEqualTo("2");
        assertThat(found.getQuestionType()).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(found.getSource()).isEqualTo(QuestionSource.MANUAL);
        assertThat(found.getPassage()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("Question 저장 - Passage 연관관계 포함, 객관식")
    void saveWithPassage() {
        // given
        Subject subject = saveSubject("q_Eng01", "q_영어01");
        QuestionCategory category = saveCategory(subject, "q_독해01", "q_Reading01");

        Passage passage = passageRepository.save(Passage.builder()
            .subject(subject)
            .title("수능 영어 지문")
            .content("Scientists have found that...")
            .build());

        Question question = Question.builder()
            .subject(subject)
            .category(category)
            .passage(passage)
            .questionType(QuestionType.MULTIPLE_CHOICE)
            .questionText("What is the main idea of the passage?")
            .choices("[\"1) ...\",\"2) ...\",\"3) ...\",\"4) ...\"]")
            .correctAnswer("2")
            .build();

        // when
        Question saved = questionRepository.save(question);
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(found.getPassage()).isNotNull();
        assertThat(found.getPassage().getPassageId()).isEqualTo(passage.getPassageId());
        assertThat(found.getQuestionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(found.getChoices()).contains("1)").contains("4)");
    }

    @Test
    @Transactional
    @DisplayName("source 기본값은 READFILE")
    void defaultSourceIsReadfile() {
        // given
        Subject subject = saveSubject("q_Math10", "q_수학10");
        QuestionCategory category = saveCategory(subject, "q_기본소스", "q_DefaultSource");

        Question question = Question.builder()
            .subject(subject)
            .category(category)
            .questionType(QuestionType.SHORT_ANSWER)
            .questionText("기본값 source 테스트")
            .correctAnswer("?")
            // source 생략 → 기본값 READFILE
            .build();

        // when
        Question saved = questionRepository.save(question);
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(found.getSource()).isEqualTo(QuestionSource.READFILE);
    }

    @Test
    @Transactional
    @DisplayName("category 없이 저장 가능 (nullable)")
    void categoryIsOptional() {
        // given
        Subject subject = saveSubject("q_Math11", "q_수학11");

        Question question = Question.builder()
            .subject(subject)
            // category 생략
            .questionType(QuestionType.SHORT_ANSWER)
            .questionText("카테고리 없는 문제")
            .correctAnswer("?")
            .build();

        // when
        Question saved = questionRepository.save(question);
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(found.getCategory()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("orderNum 기본값은 1")
    void defaultOrderNum() {
        // given
        Subject subject = saveSubject("q_Math02", "q_수학02");
        QuestionCategory category = saveCategory(subject, "q_함수01", "q_Function01");

        Question question = Question.builder()
            .subject(subject)
            .category(category)
            .questionType(QuestionType.SHORT_ANSWER)
            .questionText("f(x) = 2x, f(3)은?")
            .correctAnswer("6")
            // orderNum 생략 → 기본값 1
            .build();

        // when
        Question saved = questionRepository.save(question);
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(saved.getQuestionId()).orElseThrow();
        assertThat(found.getOrderNum()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("updateContent - 문제 내용 수정")
    void updateContent() {
        // given
        Subject subject = saveSubject("q_Sci01", "q_과학01");
        QuestionCategory category = saveCategory(subject, "q_역학01", "q_Mechanics01");

        Question question = questionRepository.save(Question.builder()
            .subject(subject)
            .category(category)
            .questionType(QuestionType.SHORT_ANSWER)
            .questionText("원래 문제")
            .correctAnswer("원래 정답")
            .explanation("원래 해설")
            .build());

        // when
        question.updateContent("수정된 문제", "수정된 정답", "수정된 해설");
        em.flush();
        em.clear();

        // then
        Question found = questionRepository.findById(question.getQuestionId()).orElseThrow();
        assertThat(found.getQuestionText()).isEqualTo("수정된 문제");
        assertThat(found.getCorrectAnswer()).isEqualTo("수정된 정답");
        assertThat(found.getExplanation()).isEqualTo("수정된 해설");
    }

    @Test
    @Transactional
    @DisplayName("questionText null이면 저장 실패")
    void questionTextNotNull() {
        // given
        Subject subject = saveSubject("q_Math04", "q_수학04");
        QuestionCategory category = saveCategory(subject, "q_통계01", "q_Statistics01");

        Question question = Question.builder()
            .subject(subject)
            .category(category)
            .questionType(QuestionType.SHORT_ANSWER)
            // questionText 누락
            .correctAnswer("정답")
            .build();

        // when & then
        assertThatThrownBy(() -> {
            questionRepository.save(question);
            em.flush();
        }).isInstanceOf(Exception.class);
    }
}
