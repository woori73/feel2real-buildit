package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GrammarBankServiceTest {
  @Mock GrammarQuestionProfileRepository profileRepository;
  @Mock GrammarTagRepository tagRepository;
  @Mock QuestionGrammarTagRepository questionTagRepository;
  @Mock GrammarQuestionVariantRepository variantRepository;
  @Mock QuestionRepository questionRepository;
  @Mock GrammarBankAccessGuard accessGuard;
  @InjectMocks GrammarBankService service;

  @Test void gradeEditByModuKeepsOriginalAndSnapshotButRequiresReview() {
    Question q = Question.builder().questionType(QuestionType.MULTIPLE_CHOICE).questionText("Test.").build();
    ReflectionTestUtils.setField(q, "questionId", 459L);
    var snapshot = Map.<String, Object>of("external_id", "WG-0459", "grade", "중3", "recommended_grade", "고1");
    var profile = GrammarQuestionProfile.builder().question(q).schoolLevel(SchoolLevel.M3)
        .reviewStatus(GrammarReviewStatus.APPROVED)
        .autoClassification(GrammarReviewMetadata.imported(null, snapshot, "WG-0459")).build();
    when(profileRepository.findByQuestion_QuestionId(459L)).thenReturn(Optional.of(profile));
    var modu = new AuthenticatedPrincipal(42L, "modu", "STUDENT");
    var response = service.updateProfile(modu, 459L,
        new GrammarProfileUpdateRequest(null, null, null, null, null, null, null, SchoolLevel.H2));
    verify(accessGuard).require(modu);
    assertThat(response.schoolLevel()).isEqualTo(SchoolLevel.M3);
    assertThat(response.recommendedGrade()).isEqualTo(SchoolLevel.H2);
    assertThat(response.reviewStatus()).isEqualTo(GrammarReviewStatus.NEEDS_REVIEW);
    assertThat(response.reviewMetadata()).isEqualTo(snapshot);
  }
}
