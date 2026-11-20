package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.LandingVideoRenderItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeBankTransfer;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.entity.LandingVideoSlotKey;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.LandingVideoService;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.HomeAgentTrailerService;
import java.util.EnumMap;
import java.util.Map;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Build It 랜딩("/") 렌더링.
 *
 * <p>Build It Shorts/Report 섹션은 관리형 영상 슬롯을 쓴다. Wiki 섹션의 기출 분석·변형문제 생성
 * 영상은 번들 정적 파일을 직접 참조하므로 모델 값이 필요 없다.
 */
@Controller
@RequiredArgsConstructor
public class LandingPageController {

  private final LandingVideoService landingVideoService;
  private final HomeAgentTrailerService homeAgentTrailerService;

  // User-approved class ad (2026-11-20, replaces the 2026-10-29 exam ad). An explicit empty setting still disables it.
  public static final String DEFAULT_PROMO_PATH = "/video/landing/f2r-class-ad-20261120.mp4";
  @Value("${app.home.exam-promo-path:" + DEFAULT_PROMO_PATH + "}")
  private String examPromoPath = DEFAULT_PROMO_PATH;

  // Public URL supplied by the user on 2026-10-29. An empty override hides the link.
  public static final String DEFAULT_REVIEW_GAME_URL = "https://app.nimblefox.ai/g/g1y7QeOYd8";
  @Value("${app.home.review-game-url:" + DEFAULT_REVIEW_GAME_URL + "}")
  private String reviewGameUrl = DEFAULT_REVIEW_GAME_URL;

  // Trailer file supplied by the user on 2026-10-29. An empty override falls back to the Studio-approved alias.
  public static final String DEFAULT_AGENT_TRAILER_PATH = "/video/landing/ai-agent-trailer-v16i-20261029.mp4";
  @Value("${app.home.agent-trailer-path:" + DEFAULT_AGENT_TRAILER_PATH + "}")
  private String agentTrailerPath = DEFAULT_AGENT_TRAILER_PATH;

  // AI AGENT 1화 YouTube video supplied by the user on 2026-11-20. It replaces the trailer slot; an empty override shows the trailer again.
  public static final String DEFAULT_AGENT_YOUTUBE_ID = "8CHtg0nkmPA";
  @Value("${app.home.agent-youtube-id:" + DEFAULT_AGENT_YOUTUBE_ID + "}")
  private String agentYoutubeId = DEFAULT_AGENT_YOUTUBE_ID;

  // 무통장입금 계좌 공개는 이 설정 하나로만 켠다(신청 기능 설정과 독립). 기본은 비공개, 운영(prd)은 승인된 설정으로 켠다.
  @Value("${app.home.bank-transfer-public:false}")
  private boolean bankTransferPublic;

  @GetMapping("/")
  public String landing(Model model) {
    model.addAttribute("examOffer", HomeExamOffer.CURRENT);
    model.addAttribute("examPromoPath", safePromoPath(examPromoPath));
    model.addAttribute("homeReviewGameUrl", safeReviewGameUrl(reviewGameUrl));
    // The game build runs inside Feel2Real; the Nimble URL stays as the fallback link.
    model.addAttribute("homeReviewGamePath", HomeReviewGameController.PAGE_PATH);
    model.addAttribute("homeAgentTrailerPath", trailerPath());
    model.addAttribute("homeAgentYoutubeId", safeYoutubeId(agentYoutubeId));
    model.addAttribute("homeBankTransfer", bankTransferPublic ? HomeBankTransfer.CURRENT : null);
    Map<LandingVideoSlotKey, LandingVideoRenderItem> videos =
        new EnumMap<>(LandingVideoSlotKey.class);
    landingVideoService.resolveRenderItems().forEach(item -> videos.put(item.slot(), item));

    model.addAttribute(
        "academyShortsVideo", required(videos, LandingVideoSlotKey.ACADEMY_SHORTS_DEMO));
    model.addAttribute(
        "parentReportVideo", required(videos, LandingVideoSlotKey.PARENT_REPORT_DEMO));
    return "public/landing";
  }

  private String trailerPath() {
    String bundled = safePromoPath(agentTrailerPath);
    if (!bundled.isEmpty()) return bundled;
    return homeAgentTrailerService.approvedMedia().isPresent() ? HomeAgentTrailerService.PUBLIC_PATH : "";
  }

  static String safePromoPath(String path) {
    return path != null && path.matches("/video/landing/[A-Za-z0-9][A-Za-z0-9_-]*\\.mp4")
        ? path : "";
  }

  /** YouTube 영상 ID(11자)만 통과시킨다. 그 밖의 값은 링크가 만들어지지 않도록 빈 문자열로 바꾼다. */
  static String safeYoutubeId(String id) {
    return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : "";
  }

  static String safeReviewGameUrl(String value) {
    if (value == null || value.isBlank()) return "";
    try {
      URI uri = URI.create(value);
      String host = uri.getHost();
      String path = uri.getRawPath();
      boolean approvedHost = "nimblefox.ai".equals(host)
          || (host != null && host.endsWith(".nimblefox.ai"));
      return "https".equals(uri.getScheme()) && approvedHost
          && uri.getRawUserInfo() == null && uri.getPort() == -1
          && uri.getRawQuery() == null && uri.getRawFragment() == null
          && path != null && path.matches("/[A-Za-z0-9_/-]+")
          && !path.matches("(?i).*/edit(?:/.*)?") ? value : "";
    } catch (IllegalArgumentException ignored) {
      return "";
    }
  }

  private LandingVideoRenderItem required(
      Map<LandingVideoSlotKey, LandingVideoRenderItem> videos, LandingVideoSlotKey slot) {
    LandingVideoRenderItem item = videos.get(slot);
    if (item == null) {
      throw new IllegalStateException("랜딩 영상 슬롯이 누락되었습니다: " + slot);
    }
    return item;
  }
}
