package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * 수업상품 대표 미디어의 종류.
 *
 * <p><b>DB 컬럼이 아니다.</b> {@code lesson_products}의 {@code media_image_key}/{@code media_video_id}
 * 에서 항상 파생되므로 컬럼으로 만들면 XOR 동기화 지점만 늘어난다. 응답 DTO의 표현 수단일 뿐이다.
 */
public enum LessonMediaType {
  IMAGE,
  VIDEO
}
