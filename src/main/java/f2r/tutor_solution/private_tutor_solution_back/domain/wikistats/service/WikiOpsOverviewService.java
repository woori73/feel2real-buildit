package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.Daily;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.Members;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.RecentMember;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.dto.WikiOpsOverviewResponse.Visits;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service.WikiVisitService.VisitSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 홈 "Wiki 운영 현황" — 방문 지표 + 가입 회원 현황을 한 번에 조립한다.
 * 회원 DB({@code users})를 그대로 사용한다(중복 회원 DB 없음).
 */
@Service
@Transactional(readOnly = true)
public class WikiOpsOverviewService {

  private static final int RECENT_MEMBER_COUNT = 8;

  private final WikiVisitService wikiVisitService;
  private final UserRepository userRepository;
  private final Clock clock;

  @Autowired
  public WikiOpsOverviewService(WikiVisitService wikiVisitService, UserRepository userRepository) {
    this(wikiVisitService, userRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  WikiOpsOverviewService(WikiVisitService wikiVisitService, UserRepository userRepository, Clock clock) {
    this.wikiVisitService = wikiVisitService;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  public WikiOpsOverviewResponse overview() {
    VisitSummary v = wikiVisitService.summary();
    Visits visits = new Visits(
        v.today(),
        v.total(),
        v.recentDays().stream().map(d -> new Daily(d.date(), d.count())).toList());

    LocalDate today = LocalDate.now(clock);
    long total = userRepository.count();
    long todayNew = userRepository.countByCreatedAtGreaterThanEqual(today.atStartOfDay());
    var recent = userRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_MEMBER_COUNT)).stream()
        .map(this::toRecentMember)
        .toList();

    return new WikiOpsOverviewResponse(visits, new Members(total, todayNew, recent));
  }

  private RecentMember toRecentMember(User u) {
    return new RecentMember(u.getDisplayName(), u.getLoginId(), u.getRole().name(), u.getCreatedAt());
  }
}
