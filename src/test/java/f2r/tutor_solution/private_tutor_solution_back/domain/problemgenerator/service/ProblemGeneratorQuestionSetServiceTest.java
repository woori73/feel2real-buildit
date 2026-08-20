package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.QuestionSetDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.QuestionSetSaveRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.QuestionUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorQuestionSet;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorSetQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorQuestionSetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorSetQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProblemGeneratorQuestionSetServiceTest {
  @Mock ProblemGeneratorQuestionSetRepository setRepository;
  @Mock ProblemGeneratorSetQuestionRepository setQuestionRepository;
  @Mock ProblemGeneratorMaterialRepository materialRepository;
  @Mock QuestionRepository questionRepository;
  @Mock PassageRepository passageRepository;
  @Mock SubjectRepository subjectRepository;
  @Mock UserRepository userRepository;

  @Test
  @DisplayName("given Gemini JSON and long paragraph when save then passage is shared and generated questions are stored")
  void save_generated_questions_with_shared_passage() {
    // given
    ProblemGeneratorQuestionSetService service = service();
    User owner = user(1L, Role.TUTOR);
    Subject english = Subject.builder().nameEn("english").nameKo("영어").build();
    ReflectionTestUtils.setField(english, "subjectId", 1);
    AtomicReference<ProblemGeneratorQuestionSet> savedSet = new AtomicReference<>();
    List<ProblemGeneratorSetQuestion> savedLinks = new ArrayList<>();
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(subjectRepository.findById(1)).thenReturn(Optional.of(english));
    when(setRepository.save(any())).thenAnswer(invocation -> {
      ProblemGeneratorQuestionSet set = invocation.getArgument(0);
      ReflectionTestUtils.setField(set, "setId", 100L);
      savedSet.set(set);
      return set;
    });
    when(passageRepository.save(any())).thenAnswer(invocation -> {
      Passage passage = invocation.getArgument(0);
      ReflectionTestUtils.setField(passage, "passageId", 200L);
      return passage;
    });
    when(questionRepository.save(any())).thenAnswer(invocation -> {
      Question question = invocation.getArgument(0);
      ReflectionTestUtils.setField(question, "questionId", 300L + savedLinks.size());
      return question;
    });
    when(setQuestionRepository.save(any())).thenAnswer(invocation -> {
      ProblemGeneratorSetQuestion link = invocation.getArgument(0);
      savedLinks.add(link);
      return link;
    });
    when(setRepository.findById(100L)).thenAnswer(invocation -> Optional.of(savedSet.get()));
    when(setQuestionRepository.findBySet_SetIdOrderByOrderNumAsc(100L)).thenReturn(savedLinks);
    String raw = "{\"summary\":\"본문 문제\",\"questions\":[{\"type\":\"MULTIPLE_CHOICE\",\"score\":3,\"stem\":\"What is true?\",\"choices\":[\"A\",\"B\"],\"answer\":\"A\",\"explanation\":\"Because A\"},{\"type\":\"SHORT_ANSWER\",\"stem\":\"Translate it.\",\"answer\":\"해석\"}]}";

    // when
    QuestionSetDetailResponse response = service.save(principal(1L, Role.TUTOR),
        new QuestionSetSaveRequest("세트", null, "현재완료", "This is a long shared paragraph.", "gemini", raw));

    // then
    assertThat(response.setId()).isEqualTo(100L);
    assertThat(response.questions()).hasSize(2);
    assertThat(response.questions().get(0).passageId()).isEqualTo(200L);
    assertThat(savedLinks).hasSize(2);
    verify(questionRepository, times(2)).save(argThat(q -> q.getSource() == QuestionSource.GENERATED));
  }

  @Test
  @DisplayName("given owned saved question when update then question and passage content are editable")
  void update_saved_question_and_passage() {
    // given
    ProblemGeneratorQuestionSetService service = service();
    User owner = user(1L, Role.TUTOR);
    Subject english = Subject.builder().nameEn("english").nameKo("영어").build();
    Passage passage = Passage.builder().subject(english).title("old").content("old paragraph").sourceOrigin("set").build();
    ReflectionTestUtils.setField(passage, "passageId", 200L);
    Question question = Question.builder().subject(english).passage(passage).questionType(QuestionType.SHORT_ANSWER)
        .questionText("old q").correctAnswer("old a").source(QuestionSource.GENERATED).build();
    ReflectionTestUtils.setField(question, "questionId", 300L);
    ProblemGeneratorQuestionSet set = ProblemGeneratorQuestionSet.builder().owner(owner).title("세트").build();
    ReflectionTestUtils.setField(set, "setId", 100L);
    ProblemGeneratorSetQuestion link = ProblemGeneratorSetQuestion.builder().set(set).question(question).orderNum(1).build();
    when(setRepository.findById(100L)).thenReturn(Optional.of(set));
    when(setQuestionRepository.findBySet_SetIdOrderByOrderNumAsc(100L)).thenReturn(List.of(link));

    // when
    var response = service.updateQuestion(principal(1L, Role.TUTOR), 100L, 300L,
        new QuestionUpdateRequest(QuestionType.MULTIPLE_CHOICE, "new q", List.of("A", "B"), "B", "new exp", "1", 5, 200L, null, "new paragraph"));

    // then
    assertThat(response.questionText()).isEqualTo("new q");
    assertThat(response.choices()).containsExactly("A", "B");
    assertThat(response.correctAnswer()).isEqualTo("B");
    assertThat(response.passageContent()).isEqualTo("new paragraph");
  }

  private ProblemGeneratorQuestionSetService service() {
    return new ProblemGeneratorQuestionSetService(setRepository, setQuestionRepository, materialRepository,
        questionRepository, passageRepository, subjectRepository, userRepository);
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal(Long id, Role role) {
    return new JwtTokenProvider.AuthenticatedPrincipal(id, role.name().toLowerCase(), role.name());
  }

  private User user(Long id, Role role) {
    User user = User.builder().loginId("user" + id).password("pw").name("사용자").nickname("닉").role(role).build();
    ReflectionTestUtils.setField(user, "userId", id);
    return user;
  }
}
