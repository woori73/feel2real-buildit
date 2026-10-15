package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.entity;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 의견 작성자가 스스로 고른 베타 참여자 유형. 랜딩 카드의 `feedback-role`로 렌더된다.
 *
 * <p>enum 선언 순서는 랜딩 폼 select의 표시 순서이며 `landing.html`의 option 순서와 일치해야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum BetaParticipantType {
  ACADEMY_PRINCIPAL("학원 원장"),
  ACADEMY_TUTOR("학원 강사"),
  STUDY_ROOM("공부방 운영"),
  TEACHING_ROOM("교습소 운영"),
  OTHER("기타 참여자");

  private final String label;

  public static List<BetaParticipantType> displayOrder() {
    return Arrays.asList(values());
  }
}
