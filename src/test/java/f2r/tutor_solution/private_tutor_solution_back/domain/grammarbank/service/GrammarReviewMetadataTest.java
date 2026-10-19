package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import static org.assertj.core.api.Assertions.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.SchoolLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrammarReviewMetadataTest {
  @Test void importedSnapshotSurvivesGradeChangesAndPreservesOriginalGrade() {
    var snapshot = Map.<String, Object>of("external_id", "WG-0459", "grade", "중3",
        "recommended_grade", "고1", "concept_code", "absolute-participial",
        "provenance", Map.of("original_ids", java.util.List.of("original-459")));
    String imported = GrammarReviewMetadata.imported(Map.of("method", "external"), snapshot, "WG-0459");
    assertThat(GrammarReviewMetadata.recommendedGrade(imported)).isEqualTo(SchoolLevel.H1);
    String edited = GrammarReviewMetadata.updateGrade(imported, SchoolLevel.H2, 7L);
    assertThat(GrammarReviewMetadata.snapshot(edited)).isEqualTo(snapshot);
    assertThat(GrammarReviewMetadata.recommendedGrade(edited)).isEqualTo(SchoolLevel.H2);
    assertThat(GrammarReviewMetadata.root(edited)).containsEntry("method", "external");
    assertThat(GrammarReviewMetadata.root(edited).get("review_history").toString())
        .contains("before=H1", "after=H2", "actorUserId=7");
    String cleared = GrammarReviewMetadata.updateGrade(edited, SchoolLevel.UNKNOWN, 7L);
    assertThat(GrammarReviewMetadata.recommendedGrade(cleared)).isEqualTo(SchoolLevel.UNKNOWN);
    assertThat(GrammarReviewMetadata.snapshot(cleared)).containsEntry("grade", "중3");
  }
  @Test void invalidIdAndGradeAreRejectedBeforePersistence() {
    assertThatThrownBy(() -> GrammarReviewMetadata.imported(null,
        Map.of("external_id", "other", "recommended_grade", "고1"), "WG-0459"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GrammarReviewMetadata.imported(null,
        Map.of("external_id", "WG-0459", "recommended_grade", "고4"), "WG-0459"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GrammarReviewMetadata.updateGrade(null, SchoolLevel.COMMON, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
  @Test void legacyClassificationIsNotAnImplicitRecommendedGrade() {
    assertThat(GrammarReviewMetadata.recommendedGrade("{\"school_level\":\"M3\"}")).isNull();
    assertThat(GrammarReviewMetadata.snapshot(null)).isEmpty();
  }
}
