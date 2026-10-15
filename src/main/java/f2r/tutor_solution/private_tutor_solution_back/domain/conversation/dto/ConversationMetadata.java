package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;

import java.util.ArrayList;
import java.util.List;

/**
 * conversations.metadata JSON 컬럼 직렬화 모델.
 * MVP §5.0 7단계 — 학생 스냅샷 + 과목 + 첨부 source 요약.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationMetadata(
    StudentSnapshot student,
    @JsonProperty("subject_id") Integer subjectId,
    List<SourceSummary> sources
) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StudentSnapshot(
      String name,
      @JsonProperty("school_name") String schoolName,
      Byte grade,
      @JsonProperty("parent_name") String parentName) {

    public static StudentSnapshot of(Student student) {
      return new StudentSnapshot(
          student.getUser().getName(),
          student.getSchoolName(),
          student.getGrade(),
          student.getParentName());
    }
  }

  /**
   * 첨부된 source 한 건의 요약. 시험지로 확정되기 전에는 sourceId/fileName만 채워지고,
   * Phase 7 — 시험지 확정 후에는 examId/isExam/examScore가 추가로 채워진다(§5.0 7단계).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SourceSummary(
      @JsonProperty("source_id") Long sourceId,
      @JsonProperty("file_name") String fileName,
      @JsonProperty("exam_id") Long examId,
      @JsonProperty("is_exam") Boolean isExam,
      @JsonProperty("exam_score") String examScore) {

    /** 첨부 시점 기본 생성자 — exam 관련 필드는 null. */
    public SourceSummary(Long sourceId, String fileName) {
      this(sourceId, fileName, null, null, null);
    }

    /** 시험지 확정 후 exam 정보 부착. fileName/sourceId는 기존값 유지. */
    public SourceSummary withExam(Long examId, String examScore) {
      return new SourceSummary(sourceId, fileName, examId, Boolean.TRUE, examScore);
    }
  }

  public static ConversationMetadata initial(Student student, Integer subjectId) {
    return new ConversationMetadata(StudentSnapshot.of(student), subjectId, new ArrayList<>());
  }

  public ConversationMetadata withSourceAppended(SourceSummary source) {
    List<SourceSummary> next = new ArrayList<>(sources == null ? List.of() : sources);
    next.add(source);
    return new ConversationMetadata(student, subjectId, next);
  }

  /**
   * sources 중 sourceId가 일치하는 항목에 exam 정보(시험지 확정 결과)를 덮어쓴 새 metadata를 반환한다.
   * 매치되는 source가 없으면 sources는 그대로 유지된 새 인스턴스를 반환한다(no-op).
   * Phase 7 — 시험지 확정 시 호출된다.
   */
  public ConversationMetadata withSourceUpdated(Long sourceId, Long examId, String examScore) {
    if (sources == null || sources.isEmpty()) {
      return new ConversationMetadata(student, subjectId, sources == null ? new ArrayList<>() : sources);
    }
    List<SourceSummary> next = new ArrayList<>(sources.size());
    for (SourceSummary s : sources) {
      if (s.sourceId() != null && s.sourceId().equals(sourceId)) {
        next.add(s.withExam(examId, examScore));
      } else {
        next.add(s);
      }
    }
    return new ConversationMetadata(student, subjectId, next);
  }

  /**
   * sources 중 sourceId가 일치하는 항목을 제거한 새 metadata를 반환한다.
   * 매치되는 source가 없으면 sources는 그대로 유지된 새 인스턴스를 반환한다(no-op).
   */
  public ConversationMetadata withSourceRemoved(Long sourceId) {
    if (sources == null || sources.isEmpty()) {
      return new ConversationMetadata(student, subjectId, sources == null ? new ArrayList<>() : sources);
    }
    List<SourceSummary> next = new ArrayList<>(sources.size());
    for (SourceSummary s : sources) {
      if (s.sourceId() == null || !s.sourceId().equals(sourceId)) {
        next.add(s);
      }
    }
    return new ConversationMetadata(student, subjectId, next);
  }

  public String toJson(ObjectMapper mapper) {
    try {
      return mapper.writeValueAsString(this);
    } catch (JacksonException e) {
      throw new IllegalStateException("conversation metadata 직렬화 실패", e);
    }
  }

  public static ConversationMetadata fromJson(String json, ObjectMapper mapper) {
    if (json == null || json.isBlank()) {
      return new ConversationMetadata(null, null, List.of());
    }
    try {
      return mapper.readValue(json, ConversationMetadata.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("conversation metadata 파싱 실패", e);
    }
  }
}
