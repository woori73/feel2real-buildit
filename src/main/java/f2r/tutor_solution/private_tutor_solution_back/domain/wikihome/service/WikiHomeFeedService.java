package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wiki.support.PublicDisplayName;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.RecentMemberItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.service.WikiLiveMessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 BuildIt Wiki 메인(/wiki) aggregate — 메인 진입 시 1회 호출로 상단 실시간 영역을 모두 조립한다.
 * (성능: N+1 없이 각 하위 서비스가 벌크 조회 + 인기 다운로드는 60초 캐시.)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WikiHomeFeedService {

  private static final int RECENT_MEMBER_COUNT = 8;
  private static final int LIVE_PREVIEW_COUNT = 6;

  private final LatestUpdatesService latestUpdatesService;
  private final RecentThreadsService recentThreadsService;
  private final PopularDownloadService popularDownloadService;
  private final WikiLiveMessageService liveMessageService;
  private final UserRepository userRepository;

  public WikiHomeFeedResponse homeFeed() {
    return new WikiHomeFeedResponse(
        latestUpdatesService.latestUpdates(),
        recentThreadsService.recentThreads(),
        recentMembers(),
        liveMessagesPreview(),
        popularDownloadService.getPopularDownloads());
  }

  private List<RecentMemberItem> recentMembers() {
    return userRepository
        .findByRoleNotOrderByCreatedAtDesc(Role.ADMIN, PageRequest.of(0, RECENT_MEMBER_COUNT))
        .stream()
        .map(u -> new RecentMemberItem(PublicDisplayName.mask(u.getName()), u.getCreatedAt()))
        .toList();
  }

  private List<f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse>
      liveMessagesPreview() {
    List<f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse> all =
        liveMessageService.recent();
    return all.size() > LIVE_PREVIEW_COUNT ? all.subList(all.size() - LIVE_PREVIEW_COUNT, all.size()) : all;
  }
}
