package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarImportResultResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GrammarBankImportServiceTest {

  @Mock GrammarQuestionProfileRepository profileRepository;
  @Mock GrammarTagRepository tagRepository;
  @Mock QuestionGrammarTagRepository questionTagRepository;
  @Mock QuestionRepository questionRepository;
  @Mock PassageRepository passageRepository;
  @Mock SubjectRepository subjectRepository;
  @Mock QuestionCategoryRepository categoryRepository;
  @Mock ProblemGeneratorMaterialRepository materialRepository;
  @Mock GrammarBankAccessGuard accessGuard;
  @Mock jakarta.persistence.EntityManager entityManager;

  @InjectMocks GrammarBankImportService service;

  private AuthenticatedPrincipal tutor() {
    return new AuthenticatedPrincipal(1L, "pjm", Role.TUTOR.name(), "stamp");
  }

  private GrammarTag tag(long id, String name, String aliases) {
    GrammarTag t = GrammarTag.builder().nameKo(name).aliases(aliases).sortOrder((int) id).build();
    ReflectionTestUtils.setField(t, "tagId", id);
    return t;
  }

  private Subject english() {
    Subject s = Subject.builder().nameEn("ENGLISH").nameKo("영어").build();
    ReflectionTestUtils.setField(s, "subjectId", 1);
    return s;
  }

  @Test
  @DisplayName("외부 분류 태그가 있으면 AUTO 등록되고, 본문 해시 동일 문항은 중복 건너뜀한다")
  void importWithExternalTagsAndDedup() {
    when(subjectRepository.findById(1)).thenReturn(Optional.of(english()));
    when(categoryRepository.findByCategoryNameKo("문법")).thenReturn(Optional.empty());
    when(tagRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
        tag(1, "수동태", "passive,by"), tag(14, "기타 문법", "")));
    AtomicReference<String> savedHash = new AtomicReference<>();
    when(profileRepository.findByContentHash(anyString())).thenAnswer(inv ->
        savedHash.get() != null && savedHash.get().equals(inv.getArgument(0))
            ? Optional.of(GrammarQuestionProfile.builder().build()) : Optional.empty());
    when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));
    when(profileRepository.save(any(GrammarQuestionProfile.class))).thenAnswer(inv -> {
      GrammarQuestionProfile p = inv.getArgument(0);
      savedHash.set(p.getContentHash());
      return p;
    });

    GrammarImportResultResponse result = service.importJson(tutor(),
        new GrammarImportRequest(null, null, List.of(
            Map.of("stem", "The bridge was built in 1990.", "answer", "built",
                "grammar_tags", List.of("수동태"), "school_level", "중2",
                "format", "객관식", "confidence", 0.9),
            Map.of("stem", "The bridge was built in 1990.", "answer", "built",
                "grammar_tags", List.of("수동태")))));

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.duplicatesSkipped()).isEqualTo(1);
    assertThat(result.heldForReview()).isZero();
    assertThat(result.isNew()).isTrue();
  }

  @Test
  @DisplayName("문법 용어가 전혀 등장하지 않으면 기타 태그·저신뢰로 검수 보류함(NEEDS_REVIEW)에 넣는다")
  void noTermMatchHeldForReview() {
    when(subjectRepository.findById(1)).thenReturn(Optional.of(english()));
    when(categoryRepository.findByCategoryNameKo("문법")).thenReturn(Optional.empty());
    when(tagRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
        tag(1, "수동태", "passive,by"), tag(14, "기타 문법", "")));
    when(profileRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
    when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));
    AtomicReference<GrammarQuestionProfile> stored = new AtomicReference<>();
    when(profileRepository.save(any(GrammarQuestionProfile.class))).thenAnswer(inv -> {
      stored.set(inv.getArgument(0));
      return stored.get();
    });

    Map<String, Object> noTerm = Map.of("stem", "Turn to page twenty and read the dialogue aloud.");
    GrammarImportResultResponse result = service.importJson(tutor(),
        new GrammarImportRequest(null, null, List.of(noTerm)));

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.heldForReview()).isEqualTo(1);
    GrammarQuestionProfile profile = stored.get();
    assertThat(profile.getReviewStatus()).isEqualTo(GrammarReviewStatus.NEEDS_REVIEW);
    assertThat(profile.getClassificationConfidence()).isNotNull();
    assertThat(profile.getAutoClassification()).contains("term-appearance");
  }

  @Test
  @DisplayName("parseCsv: 따옴표 안의 쉼표·큰따옴표를 살려 헤더 키로 매핑한다")
  void csvParsing() {
    String csv = "stem,choices,answer,grammar_tags,school_level\n"
        + "\"She said, \"\"hello\"\"\",\"(1) said (2) says\",said,조동사,중1\n";
    List<Map<String, Object>> rows = GrammarBankImportService.parseCsv(csv);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("stem")).isEqualTo("She said, \"hello\"");
    assertThat(rows.get(0).get("school_level")).isEqualTo("중1");
  }

  @Test
  @DisplayName("학년·형식 문자열을 분류 체계로 매핑하고 미매핑은 UNKNOWN으로 보존한다")
  void levelAndFormatMapping() {
    assertThat(GrammarBankImportService.parseSchoolLevel("중3")).isEqualTo(SchoolLevel.M3);
    assertThat(GrammarBankImportService.parseSchoolLevel("고1")).isEqualTo(SchoolLevel.H1);
    assertThat(GrammarBankImportService.parseSchoolLevel("2026학년도 2학기"))
        .isEqualTo(SchoolLevel.UNKNOWN);
    assertThat(GrammarQuestionFormat.fromRaw("어법상 옳은 것을 고르시오"))
        .isEqualTo(GrammarQuestionFormat.CORRECT_SENTENCE);
    assertThat(GrammarQuestionFormat.fromRaw("빈칸에 알맞은 것"))
        .isEqualTo(GrammarQuestionFormat.BLANK_FILL);
  }

  @Test
  @DisplayName("classify: 용어 등장 횟수 기반 1차 분류는 신뢰도를 0.9 상한으로 계산한다")
  void termAppearanceClassificationConfidence() {
    List<GrammarTag> tags = List.of(tag(7, "관계대명사", "who,which,that"), tag(14, "기타 문법", ""));
    GrammarBankImportService.Classification c = service.classify(Map.of(),
        "Which word is correct?", List.of("that"), tags);
    assertThat(c.tags()).isNotEmpty();
    assertThat(c.matchedNames()).containsExactly("관계대명사");
    assertThat(c.confidence()).isBetween(new BigDecimal("0.40"), new BigDecimal("0.90"));
    assertThat(c.raw()).containsEntry("method", "term-appearance");
  }
  @Test
  void reviewedIdReplayIsSkippedButChangedSnapshotIsHeldWithoutOverwrite() {
    when(subjectRepository.findById(1)).thenReturn(Optional.of(english()));
    when(tagRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
    Map<String, Object> snapshot = Map.of("external_id", "WG-0459", "grade", "중3", "recommended_grade", "고1");
    var stored = GrammarQuestionProfile.builder().externalRef("WG-0459")
        .autoClassification(GrammarReviewMetadata.imported(null, snapshot, "WG-0459")).build();
    when(profileRepository.findFirstByExternalRef("WG-0459")).thenReturn(Optional.of(stored));
    var replay = Map.<String, Object>of("external_ref", "WG-0459", "stem", "Test stem.", "review_metadata", snapshot);
    var changed = new java.util.HashMap<String, Object>(snapshot);
    changed.put("recommended_grade", "고2");
    var conflict = Map.<String, Object>of("external_ref", "WG-0459", "stem", "Changed stem.", "review_metadata", changed);
    var modu = new AuthenticatedPrincipal(42L, "modu", Role.STUDENT.name());
    var result = service.importJson(modu, new GrammarImportRequest(null, null, List.of(replay, conflict)));
    org.mockito.Mockito.verify(accessGuard).require(modu);
    assertThat(result.duplicatesSkipped()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.created()).isZero();
    assertThat(result.errors().getFirst()).contains("덮어쓰기 없이");
    org.mockito.Mockito.verifyNoInteractions(questionRepository);
    org.mockito.Mockito.verify(entityManager).lock(any(Subject.class), org.mockito.ArgumentMatchers.eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
  }
  @Test
  void reviewedImportPreservesCompleteSnapshotAndRejectsBodyMismatch() {
    when(subjectRepository.findById(1)).thenReturn(Optional.of(english()));
    when(tagRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
    when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));
    var options = List.of("being", "is", "was", "be", "been");
    var snapshot = Map.<String, Object>of("external_id", "WG-0459", "grade", "중3",
        "recommended_grade", "고1", "new_question", "The weather ___ fine, we went out.",
        "answer", "1", "choices", options.stream().map(v -> Map.of("text", v)).toList(),
        "original_ids", List.of("source-459"), "concept_code", "absolute");
    var record = Map.<String, Object>of("external_ref", "WG-0459", "stem", snapshot.get("new_question"),
        "choices", options, "answer", "1", "school_level", "M3", "review_metadata", snapshot);
    var mismatch = new java.util.HashMap<String, Object>(record);
    mismatch.put("stem", "Mismatched question.");
    var result = service.importJson(tutor(), new GrammarImportRequest(null, null, List.of(record, mismatch)));
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    var captured = org.mockito.ArgumentCaptor.forClass(GrammarQuestionProfile.class);
    org.mockito.Mockito.verify(profileRepository).save(captured.capture());
    assertThat(captured.getValue().getSchoolLevel()).isEqualTo(SchoolLevel.M3);
    assertThat(GrammarReviewMetadata.recommendedGrade(captured.getValue().getAutoClassification())).isEqualTo(SchoolLevel.H1);
    assertThat(GrammarReviewMetadata.snapshot(captured.getValue().getAutoClassification())).isEqualTo(snapshot);
  }
}
