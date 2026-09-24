package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

/**
 * Guided Agent 1단계 표시용 데이터.
 *
 * <p>프라이버시: 이 record는 정적 안내문만 담는다 — 사용자 입력값·개인정보·인증 정보는
 * 어떤 필드에도 결코 들어가지 않는다(서버는 단계 번호만 안다).
 *
 * @param number          1~14
 * @param title           단계 이름(진행 표시와 로그에 쓰이는 안전 라벨, 개인정보 없음)
 * @param where           금감원 화면에서 사용자가 있을 위치 설명
 * @param checkAction     "금감원 화면에서 ○○을 확인해주세요" 형태 지시(화면 감지 표현 금지)
 * @param confirmLabel    사용자 확인 버튼 라벨("[이 단계를 완료했습니다]" 계열)
 * @param caution         매뉴얼과 현재 화면이 다르거나 주의가 필요한 경우의 고지, 없으면 null
 * @param evidence        검증 등급
 * @param personalFields  이 단계에서 사용자가 직접 입력하는 개인정보 종류 안내, 없으면 null
 */
public record FssGuidedStep(
    int number,
    String title,
    String where,
    String checkAction,
    String confirmLabel,
    String caution,
    FssStepEvidence evidence,
    String personalFields) {

  public boolean verified() {
    return evidence != FssStepEvidence.MANUAL_UNVERIFIED;
  }
}
