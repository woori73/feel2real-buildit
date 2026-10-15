package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

/**
 * NewStart 회원 전용 실행 capability. {@code newstart_member_grants.capability}에 문자열로 저장된다.
 *
 * <p>값 추가는 enum 확장만으로 가능하고 DDL이 필요 없다. 자료·설명을 읽는 권한은 capability와
 * 무관하게 공개이고, 이 값들은 "Agent를 실제로 실행하는" 권한만 gating 한다.
 */
public enum NewStartCapability {
  /** 금감원 민원·추심 Guided Agent와 지피지기 AI 실행 권한(운영자가 DB row로 부여). */
  THANKS_MEMBER
}
