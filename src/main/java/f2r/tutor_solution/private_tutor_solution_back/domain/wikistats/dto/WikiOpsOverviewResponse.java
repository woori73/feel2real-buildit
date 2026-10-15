package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 홈 "Wiki 운영 현황" 카드 데이터. 민감 정보(비밀번호/토큰/이메일/전화)는 담지 않는다 —
 * 표시 이름·로그인 id·역할·가입일까지만.
 */
public record WikiOpsOverviewResponse(Visits visits, Members members) {

  public record Visits(long today, long total, List<Daily> recentDays) {}

  public record Daily(LocalDate date, long count) {}

  public record Members(long total, long todayNew, List<RecentMember> recent) {}

  public record RecentMember(String displayName, String loginId, String role, LocalDateTime joinedAt) {}
}
