package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * 업로드 시점에 명시적으로 고르는 영상 용도. {@code academy_videos}는 학원당 하나의 공용
 * 풀이라 용도를 함께 저장하지 않으면 원장 소개 영상과 수업 영상이 같은 목록에서 섞인다.
 *
 * <p>기존(이 필드 도입 전) 업로드 행은 {@code null}로 남는다 — 어떤 용도였는지 파일명만으로
 * 추측해 일괄 마이그레이션하지 않는다. null은 두 용도 어느 선택 목록에서도 계속 보인다
 * (하위 호환: 과거 업로드를 목록에서 잃어버리지 않는다).
 */
public enum AcademyVideoPurpose {
  /** 대표 원장 소개 영상(및 히어로 학원 소개 영상). */
  DIRECTOR,
  /** 수업/대표 수업(미니 강의) 영상. */
  LESSON
}
