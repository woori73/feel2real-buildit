package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

/** 어떤 영상이 어떤 수업상품의 대표 미디어로 쓰이는지. 삭제 차단 메시지와 목록 배지에 쓴다. */
public interface LessonMediaUsageProjection {

  Long getVideoId();

  String getLessonName();
}
