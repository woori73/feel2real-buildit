package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto;

/** 예상문제 의뢰 폼 입력. 검증은 {@code ExamRequest.create}가 담당한다. */
public record ExamRequestCreateRequest(
    String schoolName, String grade, String examRound, String examScope,
    String contact, String memo) {}
