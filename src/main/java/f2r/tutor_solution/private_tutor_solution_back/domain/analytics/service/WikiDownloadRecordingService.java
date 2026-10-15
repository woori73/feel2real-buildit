package f2r.tutor_solution.private_tutor_solution_back.domain.analytics.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiDownloadLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.entity.WikiMaterialCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.analytics.repository.WikiDownloadLogRepository;
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
 * Wiki 자료 다운로드 기록. 다운로드 endpoint가 파일을 정상적으로 읽어온 시점에만 호출한다
 * (프론트 버튼 클릭 카운트 아님). 통계 기능 실패가 다운로드 응답을 깨서는 안 되므로 예외를 삼킨다.
 */
@Slf4j
@Service
public class WikiDownloadRecordingService {

  private static final String GUEST_ROLE = "GUEST";
  private static final String GUEST_DISPLAY_NAME = "비회원";
  private static final int MAX_TITLE_LENGTH = 255;

  private final WikiDownloadLogRepository downloadRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  @Autowired
  public WikiDownloadRecordingService(WikiDownloadLogRepository downloadRepository, UserRepository userRepository) {
    this(downloadRepository, userRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  WikiDownloadRecordingService(
      WikiDownloadLogRepository downloadRepository, UserRepository userRepository, Clock clock) {
    this.downloadRepository = downloadRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Transactional
  public void record(WikiMaterialCategory category, Long materialId, String materialTitle, Long userId, String role) {
    try {
      String resolvedRole = GUEST_ROLE;
      String displayName = GUEST_DISPLAY_NAME;
      if (userId != null) {
        resolvedRole = (role == null || role.isBlank()) ? GUEST_ROLE : role;
        displayName = userRepository.findById(userId).map(User::getName).orElse(GUEST_DISPLAY_NAME);
      }
      LocalDateTime now = LocalDateTime.now(clock);
      WikiDownloadLog entry = WikiDownloadLog.of(
          now.toLocalDate(), now, category, materialId, truncateTitle(materialTitle), userId, resolvedRole,
          displayName);
      downloadRepository.save(entry);
    } catch (Exception e) {
      log.warn("Wiki 다운로드 기록 실패(category={}, materialId={}): {}", category, materialId, e.getMessage());
    }
  }

  private String truncateTitle(String title) {
    if (title == null || title.isBlank()) return "제목 없음";
    return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
  }
}
