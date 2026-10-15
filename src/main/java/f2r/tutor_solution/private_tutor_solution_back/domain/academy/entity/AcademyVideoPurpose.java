package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * 업로드 시점에 명시적으로 고르는 영상 용도. {@code academy_videos}는 학원당 하나의 공용
 * 풀이라 용도를 함께 저장하지 않으면 원장 소개 영상과 수업 영상이 같은 목록에서 섞인다.
 *
 * <p>기존(이 필드 도입 전) 업로드 행은 {@code null}로 남는다 — 어떤 용도였는지 파일명만으로
 * 추측해 일괄 마이그레이션하지 않는다. {@code null}의 의미는 앞으로도 바꾸지 않는다.
 *
 * <p>{@link #NONE}은 <b>운영자가 명시적으로 고른 "용도 지정 안 함"</b>이다(Phase 4e). 레거시
 * {@code null}과 동작(공개 화면·대표수업·원장소개 어디에도 노출 안 함)은 같지만, "한 번도
 * 분류하지 않음"이 아니라 "보관만 한다"는 의도를 담는다. DB 컬럼은 {@code VARCHAR(20)}이라
 * 스키마 변경이 없다.
 */
public enum AcademyVideoPurpose {
  /** 대표 원장 소개 영상(및 히어로 학원 소개 영상). */
  DIRECTOR,
  /** 수업/대표 수업(미니 강의) 영상. */
  LESSON,
  /** 용도 지정 안 함 — 영상 관리 목록에는 보관되지만 홈페이지 대표 영역에는 노출되지 않는다. */
  NONE
}
