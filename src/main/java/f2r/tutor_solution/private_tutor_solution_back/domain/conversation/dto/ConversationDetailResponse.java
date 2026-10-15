package f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ConversationDetailResponse(
    Long conversationId,
    String title,
    LocalDateTime createdAt,
    StudentInfo student,
    List<SourceInfo> sources,
    List<MessageInfo> messages,
    Long lessonId,
    String lessonName,
    String academyName
) {
  /**
   * Phase 12 — 대시보드 사이드바 / 시계열 호출을 위해 학생 정보 노출.
   * studentId는 score-timeline 호출 키, name/schoolName/grade는 사이드바 학생 정보 카드.
   */
  public record StudentInfo(
      Long studentId,
      String name,
      String schoolName,
      Integer grade) {
    static StudentInfo from(Conversation conv) {
      if (conv.getStudent() == null) return null;
      var s = conv.getStudent();
      return new StudentInfo(
          s.getStudentId(),
          s.getUser() != null ? s.getUser().getName() : null,
          s.getSchoolName(),
          s.getGrade() != null ? s.getGrade().intValue() : null);
    }
  }
  /**
   * 첨부된 source 한 건의 요약.
   * Phase 9 — examId/examDisplayName을 함께 노출해 프론트에서 같은 exam_id의 sources를 한 줄로 묶을 수 있게 한다.
   * Phase 10 미리보기 — pageCount를 노출해 라이트박스가 PDF 페이지 수만큼 순회할 수 있게 한다.
   */
  public record SourceInfo(
      Long sourceId,
      String fileName,
      String fileType,
      String processingStatus,
      String contentKind,
      String sourceType,
      Long examId,
      String examDisplayName,
      Integer pageCount) {
    static SourceInfo from(Source s, int pageCount) {
      Long examId = s.getExam() != null ? s.getExam().getExamId() : null;
      String examDisplayName = s.getExam() != null ? s.getExam().getDisplayName() : null;
      return new SourceInfo(
          s.getSourceId(),
          s.getOriginalFileName(),
          s.getFileType(),
          s.getProcessingStatus() != null ? s.getProcessingStatus().name() : null,
          s.getContentKind() != null ? s.getContentKind().name() : null,
          s.getSourceType() != null ? s.getSourceType().name() : null,
          examId,
          examDisplayName,
          pageCount);
    }
  }

  public record MessageInfo(Long messageId, String role, String content, LocalDateTime createdAt) {
    static MessageInfo from(ConversationMessage m) {
      return new MessageInfo(m.getMessageId(), m.getRole().name(), m.getContent(), m.getCreatedAt());
    }
  }

  public static ConversationDetailResponse from(
      Conversation conv,
      List<ConversationMessage> messages,
      List<Source> sources,
      Map<Long, Integer> pageCounts) {
    return from(conv, messages, sources, pageCounts, null, null, null);
  }

  public static ConversationDetailResponse from(
      Conversation conv,
      List<ConversationMessage> messages,
      List<Source> sources,
      Map<Long, Integer> pageCounts,
      Long lessonId,
      String lessonName,
      String academyName) {
    return new ConversationDetailResponse(
        conv.getConversationId(),
        conv.getTitle(),
        conv.getCreatedAt(),
        StudentInfo.from(conv),
        sources.stream()
            .map(s -> SourceInfo.from(s, pageCounts.getOrDefault(s.getSourceId(), 1)))
            .toList(),
        messages.stream().map(MessageInfo::from).toList(),
        lessonId,
        lessonName,
        academyName
    );
  }
}
