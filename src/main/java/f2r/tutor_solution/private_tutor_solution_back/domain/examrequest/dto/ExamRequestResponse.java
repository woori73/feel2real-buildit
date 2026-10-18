package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequestStatus;
import java.time.LocalDateTime;

public record ExamRequestResponse(
    Long id, Long userId, String schoolName, String grade, String examRound, String examScope,
    String contact, String memo, int questionCount, int priceKrw,
    ExamRequestStatus status, LocalDateTime createdAt) {

  public static ExamRequestResponse from(ExamRequest r) {
    return new ExamRequestResponse(
        r.getId(), r.getUserId(), r.getSchoolName(), r.getGrade(), r.getExamRound(),
        r.getExamScope(), r.getContact(), r.getMemo(), r.getQuestionCount(), r.getPriceKrw(),
        r.getStatus(), r.getCreatedAt());
  }
}
