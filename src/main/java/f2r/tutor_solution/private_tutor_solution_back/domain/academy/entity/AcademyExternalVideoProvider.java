package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

/**
 * 외부 링크 영상({@link AcademyVideoSourceKind#EXTERNAL_LINK})의 출처 분류.
 *
 * <p>"링크 등록 가능"과 "페이지 내부 재생(embed) 가능"은 다른 개념이다(작업지시서 §13). 이
 * 값은 서버가 URL host 로만 판정하며(리다이렉트 추적·네트워크 호출 없음), 카드 내부 embed 는
 * provider 가 공식적으로 iframe 을 허용하는 {@link #YOUTUBE} 처럼 embed URL 이 함께 저장된
 * 경우에만 사용한다. {@link #NAVER}(naver.me 단축 URL 포함)와 {@link #GENERIC} 은 embed 하지
 * 않고 "[영상 보기]" CTA 로만 노출한다.
 */
public enum AcademyExternalVideoProvider {
  NAVER,
  YOUTUBE,
  GENERIC
}
