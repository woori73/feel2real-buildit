package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMetadataTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("initial — 학생 스냅샷과 빈 sources로 초기화된다")
  void initial_creates_snapshot_with_empty_sources() {
    // given
    Student student = sampleStudent();

    // when
    ConversationMetadata metadata = ConversationMetadata.initial(student, 1);

    // then
    assertThat(metadata.student().name()).isEqualTo("홍길동");
    assertThat(metadata.student().schoolName()).isEqualTo("00중학교");
    assertThat(metadata.student().grade()).isEqualTo((byte) 3);
    assertThat(metadata.student().parentName()).isEqualTo("홍부모");
    assertThat(metadata.subjectId()).isEqualTo(1);
    assertThat(metadata.sources()).isEmpty();
  }

  @Test
  @DisplayName("toJson/fromJson — 직렬화 후 역직렬화하면 동일한 값을 갖는다")
  void roundtrips_through_json() {
    // given
    ConversationMetadata original = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "english.pdf"));

    // when
    String json = original.toJson(objectMapper);
    ConversationMetadata parsed = ConversationMetadata.fromJson(json, objectMapper);

    // then
    assertThat(json).contains("school_name", "subject_id", "source_id", "file_name");
    assertThat(parsed).isEqualTo(original);
  }

  @Test
  @DisplayName("withSourceAppended — 기존 sources에 새 source를 누적한다 (immutable copy)")
  void appends_source_immutably() {
    // given
    ConversationMetadata initial = ConversationMetadata.initial(sampleStudent(), 1);

    // when
    ConversationMetadata next = initial
        .withSourceAppended(new ConversationMetadata.SourceSummary(1L, "a.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(2L, "b.jpg"));

    // then
    assertThat(initial.sources()).isEmpty();
    assertThat(next.sources()).extracting(ConversationMetadata.SourceSummary::sourceId)
        .containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("withSourceUpdated — sourceId가 일치하는 항목에 exam 정보가 부착된다")
  void with_source_updated_attaches_exam_info() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "english.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(8L, "notes.pdf"));

    // when
    ConversationMetadata updated = metadata.withSourceUpdated(7L, 42L, "8/10");

    // then — 7번만 exam 정보 부착, 8번은 그대로
    ConversationMetadata.SourceSummary first = updated.sources().get(0);
    ConversationMetadata.SourceSummary second = updated.sources().get(1);
    assertThat(first.sourceId()).isEqualTo(7L);
    assertThat(first.examId()).isEqualTo(42L);
    assertThat(first.isExam()).isTrue();
    assertThat(first.examScore()).isEqualTo("8/10");
    assertThat(second.sourceId()).isEqualTo(8L);
    assertThat(second.examId()).isNull();
    assertThat(second.isExam()).isNull();
  }

  @Test
  @DisplayName("withSourceUpdated — 매치되는 sourceId가 없으면 sources 그대로 (no-op)")
  void with_source_updated_no_match_leaves_sources_unchanged() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "english.pdf"));

    // when
    ConversationMetadata updated = metadata.withSourceUpdated(999L, 42L, "8/10");

    // then
    assertThat(updated.sources().get(0).isExam()).isNull();
    assertThat(updated.sources().get(0).examId()).isNull();
  }

  @Test
  @DisplayName("withSourceUpdated — sources가 비어 있어도 예외 없이 동일한 metadata를 반환")
  void with_source_updated_handles_empty_sources() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1);

    // when
    ConversationMetadata updated = metadata.withSourceUpdated(7L, 42L, "8/10");

    // then
    assertThat(updated.sources()).isEmpty();
    assertThat(updated.student()).isEqualTo(metadata.student());
  }

  @Test
  @DisplayName("toJson — exam 정보가 부착된 source는 exam_id/is_exam/exam_score가 모두 포함된다")
  void to_json_includes_exam_fields_after_update() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(7L, "english.pdf"))
        .withSourceUpdated(7L, 42L, "8/10");

    // when
    String json = metadata.toJson(objectMapper);

    // then
    assertThat(json).contains("\"exam_id\":42", "\"is_exam\":true", "\"exam_score\":\"8/10\"");
  }

  @Test
  @DisplayName("fromJson — null/blank 입력이면 빈 메타데이터를 반환한다")
  void from_json_returns_empty_for_blank_input() {
    // when
    ConversationMetadata fromNull = ConversationMetadata.fromJson(null, objectMapper);
    ConversationMetadata fromBlank = ConversationMetadata.fromJson("   ", objectMapper);

    // then
    assertThat(fromNull.student()).isNull();
    assertThat(fromNull.sources()).isEmpty();
    assertThat(fromBlank.student()).isNull();
  }

  @Test
  @DisplayName("withSourceRemoved — sourceId가 일치하는 항목이 제거되고 나머지는 유지된다")
  void with_source_removed_removes_matching_source() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(1L, "a.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(2L, "b.pdf"));

    // when
    ConversationMetadata result = metadata.withSourceRemoved(1L);

    // then
    assertThat(result.sources()).extracting(ConversationMetadata.SourceSummary::sourceId)
        .doesNotContain(1L)
        .containsExactly(2L);
  }

  @Test
  @DisplayName("withSourceRemoved — 매치되는 sourceId가 없으면 sources 그대로 (예외 없음)")
  void with_source_removed_no_match_leaves_sources_unchanged() {
    // given
    ConversationMetadata metadata = ConversationMetadata.initial(sampleStudent(), 1)
        .withSourceAppended(new ConversationMetadata.SourceSummary(1L, "a.pdf"))
        .withSourceAppended(new ConversationMetadata.SourceSummary(2L, "b.pdf"));

    // when
    ConversationMetadata result = metadata.withSourceRemoved(999L);

    // then
    assertThat(result.sources()).extracting(ConversationMetadata.SourceSummary::sourceId)
        .containsExactly(1L, 2L);
  }

  private Student sampleStudent() {
    User user = User.builder()
        .loginId("std1").password("pw").name("홍길동")
        .phone("010").role(Role.STUDENT).build();
    return Student.builder()
        .user(user)
        .schoolName("00중학교")
        .grade((byte) 3)
        .parentName("홍부모")
        .parentPhone("010-1111-2222")
        .build();
  }
}
