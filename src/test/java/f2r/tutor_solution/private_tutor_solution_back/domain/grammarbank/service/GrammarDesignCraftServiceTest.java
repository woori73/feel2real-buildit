package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GrammarDesignCraftServiceTest {

  @Mock GrammarCollectionService collectionService;
  @Mock GrammarCollectionRepository collectionRepository;
  @Mock GrammarCollectionItemRepository itemRepository;
  @Mock QuestionGrammarTagRepository questionTagRepository;
  @Mock GrammarBankService bankService;

  @InjectMocks GrammarDesignCraftService service;

  @Test
  @DisplayName("원고(markdown): 문항 번호 자동 부여 · 선택지 · 목차 · 정답해설 분리")
  void buildManuscriptStructure() {
    Question q1 = Question.builder().questionType(QuestionType.MULTIPLE_CHOICE)
        .questionText("Choose the correct sentence.").choices("[\"(A) a\",\"(B) b\"]").build();
    ReflectionTestUtils.setField(q1, "questionId", 101L);
    Question q2 = Question.builder().questionType(QuestionType.SHORT_ANSWER)
        .questionText("Rewrite in passive voice.").correctAnswer("It was done.").build();
    ReflectionTestUtils.setField(q2, "questionId", 102L);

    GrammarTag tag = GrammarTag.builder().nameKo("수동태").build();
    ReflectionTestUtils.setField(tag, "tagId", 3L);
    GrammarCollection collection = GrammarCollection.builder()
        .ownerUserId(1L).kind(GrammarCollection.CollectionKind.BOOK).title("박정민 문법 문제집 1장").build();
    GrammarCollectionItem item1 = GrammarCollectionItem.builder().collection(collection)
        .question(q1).orderNum(1).build();
    GrammarCollectionItem item2 = GrammarCollectionItem.builder().collection(collection)
        .question(q2).orderNum(2).build();
    when(bankService.requireProfile(anyLong())).thenReturn(
        GrammarQuestionProfile.builder().schoolLevel(SchoolLevel.M2).build());
    when(questionTagRepository.findByQuestion_QuestionId(101L)).thenReturn(
        List.of(QuestionGrammarTag.builder().question(q1).tag(tag).build()));
    when(questionTagRepository.findByQuestion_QuestionId(102L)).thenReturn(List.of());
    when(bankService.parseChoices(q1.getChoices())).thenReturn(List.of("a", "b"));

    String md = service.buildManuscript(collection, List.of(item1, item2), "ko-textbook-a4", true);

    assertThat(md).contains("박정민 문법 문제집 1장").contains("규격: A4").contains("문항 수: 2");
    assertThat(md).contains("## 1. 중2").contains("## 2. 중2");
    assertThat(md).contains("(1) a").contains("(2) b");
    assertThat(md).contains("## 목차").contains("- 1. 중2 수동태");
    assertThat(md).contains("# 정답 및 해설").contains("정답: It was done.");
  }
}
