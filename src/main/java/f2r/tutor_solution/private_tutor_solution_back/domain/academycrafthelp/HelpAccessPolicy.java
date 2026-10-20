package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.Set;

/**
 * 문서 노출 게이트. 열람(화면)과 AI 공식 답변 근거를 분리한다.
 * - 화면: DRAFT 제외, STAFF 문서는 ADMIN/TUTOR만, audience가 ADMIN/TUTOR면 해당 역할(또는 ADMIN)만.
 * - AI 근거: 설정된 공식 상태(기본 REVIEWED)만. DRAFT·REVIEW는 기본적으로 일반 답변에 쓰지 않는다.
 */
public final class HelpAccessPolicy {

  private final Set<String> aiOfficialStatuses;

  public HelpAccessPolicy(Set<String> aiOfficialStatuses) {
    this.aiOfficialStatuses = Set.copyOf(aiOfficialStatuses);
  }

  public boolean canView(HelpDoc doc, String role) {
    if ("DRAFT".equals(doc.status())) {
      return false;
    }
    return roleAllowed(doc, role);
  }

  public boolean canUseForAi(HelpDoc doc, String role) {
    return aiOfficialStatuses.contains(doc.status()) && roleAllowed(doc, role);
  }

  private boolean roleAllowed(HelpDoc doc, String role) {
    boolean staff = "ADMIN".equals(role) || "TUTOR".equals(role);
    if (!staff) {
      return false;
    }
    return switch (doc.audience()) {
      case "ADMIN" -> "ADMIN".equals(role);
      case "TUTOR" -> "TUTOR".equals(role) || "ADMIN".equals(role);
      default -> true;
    };
  }
}
