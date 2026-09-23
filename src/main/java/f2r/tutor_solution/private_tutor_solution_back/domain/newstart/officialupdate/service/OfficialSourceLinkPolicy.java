package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "공식 출처 확인" 보조 외부 링크로 노출해도 되는 URL인지 판정한다.
 *
 * <p>NewStart 내부 상세보기가 기본 열람 경로가 되면서 원문 URL은 provenance 용도로만 쓰인다.
 * 따라서 형식 검증(normalizeUrl)을 통과해도 출처 기관별 실존 호스트 allowlist 에 없으면
 * 검증 실패로 간주해 링크를 표시하지 않는다(400/404 페이지로 사용자를 보내지 않기 위함).
 * 본문 내용은 이 정책과 무관하게 항상 내부 상세에서 열람 가능하다.
 */
public final class OfficialSourceLinkPolicy {

  /** Discovery 어댑터에서 실제 사용하는 공식 호스트만 허용한다 (docs가 아닌 코드 근거). */
  private static final Map<SourceType, Set<String>> ALLOWED_HOSTS = Map.of(
      SourceType.NEWSTART_FUND, Set.of("www.newstartfund.or.kr"),
      SourceType.CCRS, Set.of("www.ccrs.or.kr"),
      SourceType.FSC, Set.of("www.fsc.go.kr"),
      SourceType.HOPE_RETURN_PACKAGE, Set.of("sbiz24.kr", "www.sbiz24.kr"));

  private static final Map<String, String> BARE_TO_CANONICAL = Map.of(
      "newstartfund.or.kr", "www.newstartfund.or.kr",
      "ccrs.or.kr", "www.ccrs.or.kr",
      "fsc.go.kr", "www.fsc.go.kr");

  private OfficialSourceLinkPolicy() {
  }

  /**
   * 저장된 원문 URL 을 검증 가능한 canonical https URL 로 되돌린다.
   *
   * @return 검증 성공 시 표시 가능한 절대 URL, 실패 시 null (링크 미표시)
   */
  public static String verifiedUrl(SourceType sourceType, String rawUrl) {
    if (sourceType == null) {
      return null;
    }
    Set<String> allowed = ALLOWED_HOSTS.get(sourceType);
    if (allowed == null) {
      return null; // 호스트 근거가 없는 SourceType 은 외부 링크를 내보내지 않는다.
    }
    String normalized;
    try {
      normalized = NewStartOfficialUpdateService.normalizeUrl(rawUrl);
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (normalized == null) {
      return null;
    }
    URI uri;
    try {
      uri = new URI(normalized);
    } catch (Exception e) {
      return null;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      return null;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    String canonical = BARE_TO_CANONICAL.get(host);
    if (canonical != null) {
      normalized = normalized.replaceFirst("^(https?)://" + host, "$1://" + canonical);
      host = canonical;
    }
    return allowed.contains(host) ? normalized : null;
  }
}
