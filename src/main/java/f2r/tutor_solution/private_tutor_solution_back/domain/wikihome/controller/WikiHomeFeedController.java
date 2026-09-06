package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.PopularDownloads;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.PopularDownloadService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service.WikiHomeFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 BuildIt Wiki 메인(/wiki) 데이터 API. 전부 비회원 포함 공개(SecurityConfig permitAll).
 * <ul>
 *   <li>{@code GET /api/wiki/home-feed} — 메인 진입 1회. 최신 업데이트·최근 문항·최근 회원·LIVE·인기 다운로드.
 *   <li>{@code GET /api/wiki/popular-downloads} — 인기 다운로드만 주기적으로 가볍게 재조회(30~60초).
 * </ul>
 * 관리자 전용 Analytics API 는 프론트에서 호출하지 않는다 — 여기 응답에는 개인정보가 없다.
 */
@RestController
@RequiredArgsConstructor
public class WikiHomeFeedController {

  private final WikiHomeFeedService homeFeedService;
  private final PopularDownloadService popularDownloadService;

  @GetMapping("/api/wiki/home-feed")
  public ResponseEntity<WikiHomeFeedResponse> homeFeed() {
    return ResponseEntity.ok(homeFeedService.homeFeed());
  }

  @GetMapping("/api/wiki/popular-downloads")
  public ResponseEntity<PopularDownloads> popularDownloads() {
    return ResponseEntity.ok(popularDownloadService.getPopularDownloads());
  }
}
