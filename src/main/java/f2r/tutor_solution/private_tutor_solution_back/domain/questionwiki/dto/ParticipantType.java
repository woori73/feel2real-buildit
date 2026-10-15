package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;

/** 공개 가능한 participant type. 실제 계정 role 을 그대로 노출하지 않고 표시용 라벨로 변환한다. */
public final class ParticipantType {

  private ParticipantType() {}

  public static String label(Role role) {
    if (role == null) {
      return "참여자";
    }
    return switch (role) {
      case TUTOR -> "교육자";
      case STUDENT -> "학생";
      case ADMIN -> "운영자";
      // CHOONGGI 전용 role — Question Wiki와 무관하므로 일반 참여자로 표시한다.
      case CREATOR -> "참여자";
    };
  }
}
