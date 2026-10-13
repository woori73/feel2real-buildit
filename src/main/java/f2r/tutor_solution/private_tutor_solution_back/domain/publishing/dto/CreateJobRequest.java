package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.dto;

/** 출판 작업 생성 요청. */
public record CreateJobRequest(
    String title,
    String template
) {
}
