package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloads;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.PopularDownloadService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.WikiHomeFeedService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt Wiki 메인(/wiki) 소개용 데이터 API.
 * <ul>
 *   <li>비회원: 최신 자료 제목/분류와 인기 자료 메타데이터만 반환한다.
 *   <li>회원: 기존과 동일하게 최근 Thread·회원·LIVE 활동까지 반환한다.
 * </ul>
 * 보호 본문은 CSS/JS로 숨기지 않고 익명 서버 응답에서 아예 제외한다.
 */
@RestController
@RequiredArgsConstructor
public class WikiHomeFeedController {

  private final WikiHomeFeedService homeFeedService;
  private final PopularDownloadService popularDownloadService;

  @GetMapping("/api/wiki/home-feed")
  public ResponseEntity<WikiHomeFeedResponse> homeFeed(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    WikiHomeFeedResponse feed = homeFeedService.homeFeed();
    if (principal == null) {
      feed = new WikiHomeFeedResponse(
          feed.latestUpdates(), List.of(), List.of(), List.of(), feed.popularDownloads());
    }
    return ResponseEntity.ok(feed);
  }

  @GetMapping("/api/wiki/popular-downloads")
  public ResponseEntity<PopularDownloads> popularDownloads() {
    return ResponseEntity.ok(popularDownloadService.getPopularDownloads());
  }
}
