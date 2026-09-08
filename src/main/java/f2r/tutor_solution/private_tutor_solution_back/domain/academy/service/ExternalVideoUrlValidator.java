package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyExternalVideoProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 외부 링크 영상 등록 시 사용자 입력 URL 을 검증·분류한다(작업지시서 §13·§18).
 *
 * <p><b>네트워크 호출을 하지 않는다</b> — naver.me 같은 단축 URL 의 리다이렉트를 서버가 따라가지
 * 않는다(SSRF 방지). host 문자열로만 provider 를 판정하고, 카드 내부 embed 는 provider 가
 * 공식적으로 iframe 을 허용하는 경우(현재 YouTube)만 embed URL 을 만든다. 그 외에는 embed 하지
 * 않고 "[영상 보기]" CTA 로만 노출한다.
 *
 * <p>허용 scheme 은 {@code https} 뿐이다. {@code http} / {@code javascript:} / {@code data:} 등은
 * 거부한다. 반환하는 {@link Resolved#normalizedUrl()} 은 입력을 trim 한 원문이며, 프론트는 이
 * 값을 raw HTML 로 삽입하지 않고 항상 {@code href}/{@code src} 속성 또는 textContent 로만 쓴다.
 */
@Component
public class ExternalVideoUrlValidator {

  private static final int MAX_URL_LENGTH = 2048;

  /** YouTube video id: 표준은 11자지만 향후 변화를 감안해 폭을 약간 넓게 잡는다. */
  private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{6,20}$");
  private static final Pattern YOUTUBE_PATH_ID =
      Pattern.compile("^/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{6,20})");

  /**
   * 네이버 TV 클립만 공식 embed 가 있다: {@code tv.naver.com/{v|clip|embed}/<clipNo>} →
   * {@code https://tv.naver.com/embed/<clipNo>}. clipNo 는 숫자다. naver.me 단축주소와
   * {@code m.naver.com/shorts}(GUID mediaId) 자체는 공식 embed 경로가 없어 embed 하지 않는다.
   */
  private static final Pattern NAVER_TV_CLIP_NO =
      Pattern.compile("^/(?:v|clip|embed)/(\\d{1,15})(?:[/?#]|$)");

  /**
   * 네이버 쇼츠 공식 oembed player. "공유 → 소스코드" 의 iframe src 형식:
   * {@code https://clip-viewer.naver.com/oembed/?seedMediaId=<HEX>&mediaType=SHORT_FORM&serviceType=NTV}.
   * 보안상 매우 좁게 allowlist 한다 — host/path 정확 일치 + 세 쿼리 파라미터가 정확히 이 값일 때만.
   */
  private static final String NAVER_OEMBED_HOST = "clip-viewer.naver.com";
  private static final Pattern NAVER_OEMBED_SEED_ID = Pattern.compile("^[0-9A-Fa-f]{16,64}$");

  public record Resolved(
      String normalizedUrl,
      AcademyExternalVideoProvider provider,
      String embedUrl) {}

  public Resolved validate(String rawUrl) {
    String url = rawUrl == null ? "" : rawUrl.trim();
    if (url.isEmpty()) {
      throw reject("영상 URL을 입력해 주세요.");
    }
    if (url.length() > MAX_URL_LENGTH) {
      throw reject("영상 URL이 너무 깁니다.");
    }

    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw reject("영상 URL 형식이 올바르지 않습니다.");
    }

    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"https".equals(scheme)) {
      throw reject("영상 URL은 https:// 주소만 등록할 수 있습니다.");
    }
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    if (host.isEmpty()) {
      throw reject("영상 URL의 주소를 확인할 수 없습니다.");
    }

    if (isYoutubeHost(host)) {
      String videoId = extractYoutubeId(host, uri);
      if (videoId != null) {
        return new Resolved(url, AcademyExternalVideoProvider.YOUTUBE,
            "https://www.youtube.com/embed/" + videoId);
      }
      // YouTube 도메인이지만 재생 id 를 못 뽑으면(채널/재생목록 등) embed 하지 않는다.
      return new Resolved(url, AcademyExternalVideoProvider.GENERIC, null);
    }
    if (host.equals(NAVER_OEMBED_HOST)) {
      String seedEmbed = naverOembedEmbedUrl(uri);
      return new Resolved(url, AcademyExternalVideoProvider.NAVER, seedEmbed);  // 불일치면 null → CTA
    }
    if (isNaverTvHost(host)) {
      String clipNo = extractNaverTvClipNo(uri);
      if (clipNo != null) {
        return new Resolved(url, AcademyExternalVideoProvider.NAVER,
            "https://tv.naver.com/embed/" + clipNo);
      }
      // tv.naver.com 이지만 clip 번호를 못 뽑으면(채널 등) embed 하지 않는다.
      return new Resolved(url, AcademyExternalVideoProvider.NAVER, null);
    }
    if (isNaverHost(host)) {
      // naver.me 단축주소 / m.naver.com/shorts(GUID) 등 — 공식 embed 없음. CTA 로만 노출한다.
      return new Resolved(url, AcademyExternalVideoProvider.NAVER, null);
    }
    return new Resolved(url, AcademyExternalVideoProvider.GENERIC, null);
  }

  /**
   * clip-viewer.naver.com/oembed/ 를 매우 좁게 검증한다: path 가 정확히 {@code /oembed/}(또는
   * {@code /oembed}) 이고, {@code seedMediaId} 가 HEX 이며, {@code mediaType=SHORT_FORM} +
   * {@code serviceType=NTV} 일 때만 canonical embed URL 을 재구성해 돌려준다(입력의 여분
   * 파라미터·순서는 버린다). 하나라도 어긋나면 null → CTA fallback.
   */
  private static String naverOembedEmbedUrl(URI uri) {
    String path = uri.getPath() == null ? "" : uri.getPath();
    if (!path.equals("/oembed/") && !path.equals("/oembed")) {
      return null;
    }
    String query = uri.getRawQuery();
    if (query == null || query.isEmpty()) {
      return null;
    }
    String seedMediaId = null;
    boolean shortForm = false;
    boolean ntv = false;
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq <= 0) continue;
      String k = pair.substring(0, eq);
      String v = urlDecode(pair.substring(eq + 1));
      if ("seedMediaId".equals(k)) {
        seedMediaId = v;
      } else if ("mediaType".equals(k)) {
        shortForm = "SHORT_FORM".equals(v);
      } else if ("serviceType".equals(k)) {
        ntv = "NTV".equals(v);
      }
    }
    if (seedMediaId == null || !NAVER_OEMBED_SEED_ID.matcher(seedMediaId).matches()
        || !shortForm || !ntv) {
      return null;
    }
    return "https://clip-viewer.naver.com/oembed/?seedMediaId=" + seedMediaId
        + "&mediaType=SHORT_FORM&serviceType=NTV";
  }

  private static String urlDecode(String value) {
    try {
      return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      return value;
    }
  }

  private static boolean isNaverTvHost(String host) {
    return host.equals("tv.naver.com") || host.equals("m.tv.naver.com");
  }

  private static String extractNaverTvClipNo(URI uri) {
    String path = uri.getPath() == null ? "" : uri.getPath();
    Matcher m = NAVER_TV_CLIP_NO.matcher(path);
    return m.find() ? m.group(1) : null;
  }

  private static boolean isYoutubeHost(String host) {
    return host.equals("youtube.com") || host.endsWith(".youtube.com")
        || host.equals("youtu.be") || host.endsWith(".youtu.be")
        || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com");
  }

  private static boolean isNaverHost(String host) {
    return host.equals("naver.me") || host.endsWith(".naver.me")
        || host.equals("naver.com") || host.endsWith(".naver.com");
  }

  private static String extractYoutubeId(String host, URI uri) {
    if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
      String path = uri.getPath() == null ? "" : uri.getPath();
      String candidate = path.startsWith("/") ? path.substring(1) : path;
      int slash = candidate.indexOf('/');
      if (slash >= 0) candidate = candidate.substring(0, slash);
      return YOUTUBE_ID.matcher(candidate).matches() ? candidate : null;
    }
    String query = uri.getQuery();
    if (query != null) {
      for (String pair : query.split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0 && "v".equals(pair.substring(0, eq))) {
          String candidate = pair.substring(eq + 1);
          if (YOUTUBE_ID.matcher(candidate).matches()) return candidate;
        }
      }
    }
    String path = uri.getPath() == null ? "" : uri.getPath();
    Matcher m = YOUTUBE_PATH_ID.matcher(path);
    return m.find() ? m.group(1) : null;
  }

  private static VideoUploadRejectedException reject(String message) {
    return new VideoUploadRejectedException(message);
  }
}
