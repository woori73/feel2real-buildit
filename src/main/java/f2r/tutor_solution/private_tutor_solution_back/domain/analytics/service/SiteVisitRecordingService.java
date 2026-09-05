package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.SitePageVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsServiceArea;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.AnalyticsTrafficKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.SitePageVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F2R 전체/Wiki 페이지 방문 기록. {@code SiteVisitFilter}가 실제 HTML 페이지 응답에서만 호출한다.
 * 통계 기능 실패가 페이지 응답을 깨서는 안 되므로 예외를 삼킨다(로그만 남김).
 */
@Slf4j
@Service
public class SiteVisitRecordingService {

  private static final String GUEST_ROLE = "GUEST";
  private static final String GUEST_DISPLAY_NAME = "비회원";
  private static final int MAX_PATH_LENGTH = 255;

  private final SitePageVisitRepository visitRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  @Autowired
  public SiteVisitRecordingService(SitePageVisitRepository visitRepository, UserRepository userRepository) {
    this(visitRepository, userRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  SiteVisitRecordingService(SitePageVisitRepository visitRepository, UserRepository userRepository, Clock clock) {
    this.visitRepository = visitRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Transactional
  public void record(String path, boolean wiki, String visitorKey, Long userId, String role) {
    record(path, visitorKey, userId, role, AnalyticsTrafficKind.HUMAN);
  }

  @Transactional
  public void record(String path, String visitorKey, Long userId, String role,
      AnalyticsTrafficKind trafficKind) {
    try {
      String resolvedRole = GUEST_ROLE;
      String displayName = GUEST_DISPLAY_NAME;
      if (userId != null) {
        resolvedRole = (role == null || role.isBlank()) ? GUEST_ROLE : role;
        displayName = userRepository.findById(userId).map(User::getName).orElse(GUEST_DISPLAY_NAME);
      }
      LocalDateTime now = LocalDateTime.now(clock);
      AnalyticsServiceArea serviceArea = AnalyticsServiceAreaClassifier.classify(path);
      SitePageVisit visit = SitePageVisit.of(
          now.toLocalDate(), now, serviceArea == AnalyticsServiceArea.WIKI, truncatePath(path),
          userId, resolvedRole, displayName, visitorKey, serviceArea, trafficKind);
      visitRepository.save(visit);
    } catch (Exception e) {
      log.warn("사이트 방문 기록 실패(path={}): {}", path, e.getMessage());
    }
  }

  private String truncatePath(String path) {
    if (path == null) return "";
    return path.length() > MAX_PATH_LENGTH ? path.substring(0, MAX_PATH_LENGTH) : path;
  }
}
