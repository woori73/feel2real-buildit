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
        GrammarQuestionProfile.builder().schoolLevel(SchoolLevel.M2)
            .difficulty(GrammarQuestionProfile.GrammarDifficulty.HARD)
            .autoClassification(GrammarReviewMetadata.imported(null,
                java.util.Map.of("external_id", "WG-0459", "recommended_grade", "고1", "grade", "중2"), "WG-0459")).build());
    when(questionTagRepository.findByQuestion_QuestionId(101L)).thenReturn(
        List.of(QuestionGrammarTag.builder().question(q1).tag(tag).build()));
    when(questionTagRepository.findByQuestion_QuestionId(102L)).thenReturn(List.of());
    when(bankService.parseChoices(q1.getChoices())).thenReturn(List.of("a", "b"));

    String md = service.buildManuscript(collection, List.of(item1, item2), "ko-textbook-a4", true);

    assertThat(md).contains("박정민 문법 문제집 1장").contains("규격: A4").contains("문항 수: 2");
    assertThat(md).contains("## 1. 권장 고1 · 심화 (기존 중2)").contains("## 2. 권장 고1 · 심화 (기존 중2)");
    assertThat(md).contains("(1) a").contains("(2) b");
    assertThat(md).contains("## 목차").contains("- 1. 권장 고1 · 심화 (기존 중2) 수동태");
    assertThat(md).contains("# 정답 및 해설").contains("정답: It was done.");
  }
  @Test
  void jsonExportKeepsRecommendedAndOriginalClassificationsAndCompleteSnapshot() throws Exception {
    var principal = new f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal(42L, "modu", "STUDENT");
    var collection = GrammarCollection.builder().ownerUserId(42L).kind(GrammarCollection.CollectionKind.BOOK).title("고1 분사").build();
    var q = Question.builder().questionType(QuestionType.MULTIPLE_CHOICE).questionText("Test.").correctAnswer("1").build();
    ReflectionTestUtils.setField(q, "questionId", 459L);
    var item = GrammarCollectionItem.builder().collection(collection).question(q).orderNum(1).build();
    var snapshot = java.util.Map.<String, Object>of("external_id", "WG-0459", "grade", "중3", "recommended_grade", "고1", "original_ids", List.of("source-459"));
    var profile = GrammarQuestionProfile.builder().question(q).schoolLevel(SchoolLevel.M3)
        .difficulty(GrammarQuestionProfile.GrammarDifficulty.HARD).externalRef("WG-0459")
        .autoClassification(GrammarReviewMetadata.imported(null, snapshot, "WG-0459")).build();
    when(collectionService.loadOwned(principal, 1L)).thenReturn(collection);
    when(itemRepository.findByCollection_CollectionIdOrderByOrderNumAsc(1L)).thenReturn(List.of(item));
    when(bankService.requireProfile(459L)).thenReturn(profile);
    var exported = service.exportJson(principal, 1L);
    var row = new com.fasterxml.jackson.databind.ObjectMapper().readTree(exported.content()).get("questions").get(0);
    assertThat(row.get("schoolLevel").asText()).isEqualTo("M3");
    assertThat(row.get("recommendedGrade").asText()).isEqualTo("H1");
    assertThat(row.get("difficulty").asText()).isEqualTo("HARD");
    assertThat(row.get("reviewMetadata").get("original_ids").get(0).asText()).isEqualTo("source-459");
    assertThat(row.get("reviewMetadata").get("grade").asText()).isEqualTo("중3");
  }
}
