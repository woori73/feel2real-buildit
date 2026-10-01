package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceMemberView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.PresenceSettingsView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.entity.NewStartMemberPresence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.repository.NewStartMemberPresenceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 접속상태(heartbeat) + 표시/수신 설정.
 *
 * <p>온라인 판정은 {@code last_seen_at >= now - 5분}이면서 숨김 affiliates가
 * 아닌 행이다. 존재하지 않는 행(한 번도 ping하지 않음)은 오프라인이다.
 * GRANT/OAuth/THANKS 판정과 무관한 별도 기능이다.
 */
@Service
public class NewStartPresenceService {

  static final long ONLINE_WINDOW_MINUTES = 5;
  static final long PING_THROTTLE_SECONDS = 60;
  static final int MAX_ONLINE_MEMBERS = 100;

  private final NewStartMemberPresenceRepository presenceRepository;
  private final UserRepository userRepository;
  private final NewStartMemberProfileService memberProfileService;
  private final Clock clock;

  @Autowired
  public NewStartPresenceService(
      NewStartMemberPresenceRepository presenceRepository,
      UserRepository userRepository,
      NewStartMemberProfileService memberProfileService) {
    this(presenceRepository, userRepository, memberProfileService, Clock.systemUTC());
  }

  /** 테스트용 — 고정 Clock 주입. */
  public NewStartPresenceService(
      NewStartMemberPresenceRepository presenceRepository,
      UserRepository userRepository,
      NewStartMemberProfileService memberProfileService,
      Clock clock) {
    this.presenceRepository = presenceRepository;
    this.userRepository = userRepository;
    this.memberProfileService = memberProfileService;
    this.clock = clock;
  }

  /** heartbeat 수신. 60초 이내 중복 ping은 쓰지 않는다. 본인 설정값을 돌려준다. */
  @Transactional
  public PresenceSettingsView ping(JwtTokenProvider.AuthenticatedPrincipal principal) {
    NewStartMemberPresence row = presenceRepository.findById(principal.userId())
        .orElseGet(() -> NewStartMemberPresence.builder().userId(principal.userId()).build());
    LocalDateTime now = LocalDateTime.now(clock);
    if (row.getLastSeenAt() == null
        || Duration.between(row.getLastSeenAt(), now).getSeconds() >= PING_THROTTLE_SECONDS) {
      row.setLastSeenAt(now);
      presenceRepository.save(row);
    }
    return new PresenceSettingsView(
        Boolean.TRUE.equals(row.getHideOnline()),
        Boolean.TRUE.equals(row.getDmBlocked()));
  }

  /** 현재 접속 회원 목록(본인 제외, 최근 본 순, 최대 100). */
  @Transactional(readOnly = true)
  public List<PresenceMemberView> onlineMembers(JwtTokenProvider.AuthenticatedPrincipal principal) {
    LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(ONLINE_WINDOW_MINUTES);
    List<NewStartMemberPresence> rows =
        presenceRepository.findByLastSeenAtAfterAndHideOnlineFalseOrderByLastSeenAtDesc(cutoff);
    List<Long> userIds = rows.stream()
        .map(NewStartMemberPresence::getUserId)
        .filter(id -> !id.equals(principal.userId()))
        .distinct()
        .limit(MAX_ONLINE_MEMBERS)
        .toList();
    if (userIds.isEmpty()) {
      return List.of();
    }
    List<User> users = userRepository.findAllById(userIds);
    Map<Long, String> nicknames = memberProfileService.resolveDisplayNicknames(users);
    Map<Long, LocalDateTime> seenByUser = rows.stream()
        .collect(java.util.stream.Collectors.toMap(
            NewStartMemberPresence::getUserId,
            NewStartMemberPresence::getLastSeenAt,
            (a, b) -> a));
    return users.stream()
        .map(u -> new PresenceMemberView(
            u.getUserId(),
            nicknames.getOrDefault(u.getUserId(), u.getDisplayName()),
            seenByUser.get(u.getUserId())))
        .sorted((a, b) -> b.lastSeenAt().compareTo(a.lastSeenAt()))
        .toList();
  }

  /** 본인 설정 조회(행이 없으면 기본값). */
  @Transactional(readOnly = true)
  public PresenceSettingsView getSettings(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return presenceRepository.findById(principal.userId())
        .map(row -> new PresenceSettingsView(
            Boolean.TRUE.equals(row.getHideOnline()),
            Boolean.TRUE.equals(row.getDmBlocked())))
        .orElse(new PresenceSettingsView(false, false));
  }

  /** 본인 설정 변경. null 필드는 그대로 둔다. */
  @Transactional
  public PresenceSettingsView updateSettings(
      JwtTokenProvider.AuthenticatedPrincipal principal, Boolean hideOnline, Boolean dmBlocked) {
    NewStartMemberPresence row = presenceRepository.findById(principal.userId())
        .orElseGet(() -> NewStartMemberPresence.builder().userId(principal.userId()).build());
    if (hideOnline != null) {
      row.setHideOnline(hideOnline);
    }
    if (dmBlocked != null) {
      row.setDmBlocked(dmBlocked);
    }
    presenceRepository.save(row);
    return new PresenceSettingsView(
        Boolean.TRUE.equals(row.getHideOnline()),
        Boolean.TRUE.equals(row.getDmBlocked()));
  }
}
