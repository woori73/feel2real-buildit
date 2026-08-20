package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Rewrites an exact {@code /archived} route into its canonical target path in the initial request.
 * Downstream security filters and MVC therefore evaluate one path without a servlet forward.
 */
public class ArchivedRouteAliasFilter extends OncePerRequestFilter {

  public static final String ARCHIVED_ATTRIBUTE =
      ArchivedRouteAliasFilter.class.getName() + ".archived";
  public static final String ORIGINAL_URI_ATTRIBUTE =
      ArchivedRouteAliasFilter.class.getName() + ".originalUri";

  private static final String ARCHIVED_PREFIX = "/archived";

  private final ArchivedRoutesProperties properties;

  public ArchivedRouteAliasFilter(ArchivedRoutesProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = pathWithinContext(request);
    return request.getDispatcherType() != DispatcherType.REQUEST
        || (!path.equals(ARCHIVED_PREFIX) && !path.startsWith(ARCHIVED_PREFIX + "/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!properties.enabled()) {
      response.sendError(HttpStatus.NOT_FOUND.value());
      return;
    }

    String originalUri = request.getRequestURI();
    String contextPath = normalizeContextPath(request.getContextPath());
    String archivedPath = originalUri.substring((contextPath + ARCHIVED_PREFIX).length());
    String targetPath = archivedPath.isEmpty() || "/".equals(archivedPath) ? "/home" : archivedPath;

    if (!isSafeTarget(targetPath)) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "잘못된 보존 경로입니다.");
      return;
    }

    String targetUri = contextPath + targetPath;
    HttpServletRequest wrapped = new CanonicalPathRequest(request, targetUri, targetPath);
    wrapped.setAttribute(ARCHIVED_ATTRIBUTE, Boolean.TRUE);
    wrapped.setAttribute(ORIGINAL_URI_ATTRIBUTE, originalUri);

    // Both Spring's parsed RequestPath and the legacy lookup-path cache are request attributes.
    // Replace them after wrapping so no pre-cached /archived path survives downstream matching.
    ServletRequestPathUtils.clearParsedRequestPath(wrapped);
    wrapped.removeAttribute(UrlPathHelper.PATH_ATTRIBUTE);
    ServletRequestPathUtils.parseAndCache(wrapped);
    UrlPathHelper.defaultInstance.resolveAndCacheLookupPath(wrapped);

    filterChain.doFilter(wrapped, response);
  }

  private boolean isSafeTarget(String targetPath) {
    if (!targetPath.startsWith("/")) {
      return false;
    }
    String rawFirstSegment = firstSegment(targetPath);
    String decodedFirstSegment;
    try {
      decodedFirstSegment = UriUtils.decode(rawFirstSegment, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      return false;
    }

    if (decodedFirstSegment.isEmpty()
        || ".".equals(decodedFirstSegment)
        || "..".equals(decodedFirstSegment)
        || decodedFirstSegment.indexOf('/') >= 0
        || decodedFirstSegment.indexOf('\\') >= 0
        || decodedFirstSegment.indexOf(';') >= 0
        || rawFirstSegment.indexOf(';') >= 0) {
      return false;
    }
    if ("archived".equals(decodedFirstSegment)) {
      return false;
    }
    // The reserved API boundary must be literal. Encoded spellings could make security and
    // Origin matchers disagree about whether this is an API request.
    return !"api".equals(decodedFirstSegment) || "api".equals(rawFirstSegment);
  }

  private String firstSegment(String path) {
    int nextSlash = path.indexOf('/', 1);
    return nextSlash < 0 ? path.substring(1) : path.substring(1, nextSlash);
  }

  private String pathWithinContext(HttpServletRequest request) {
    String contextPath = normalizeContextPath(request.getContextPath());
    String requestUri = request.getRequestURI();
    return requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;
  }

  private String normalizeContextPath(String contextPath) {
    return contextPath == null ? "" : contextPath;
  }

  private static final class CanonicalPathRequest extends HttpServletRequestWrapper {

    private final String requestUri;
    private final String servletPath;
    private final String requestUrl;

    private CanonicalPathRequest(
        HttpServletRequest request, String requestUri, String servletPath) {
      super(request);
      this.requestUri = requestUri;
      this.servletPath = servletPath;
      String originalUrl = request.getRequestURL().toString();
      String originalUri = request.getRequestURI();
      this.requestUrl = originalUrl.endsWith(originalUri)
          ? originalUrl.substring(0, originalUrl.length() - originalUri.length()) + requestUri
          : originalUrl;
    }

    @Override
    public String getRequestURI() {
      return requestUri;
    }

    @Override
    public String getServletPath() {
      return servletPath;
    }

    @Override
    public StringBuffer getRequestURL() {
      return new StringBuffer(requestUrl);
    }

    @Override
    public String getPathInfo() {
      return super.getPathInfo() == null ? null : servletPath;
    }
  }
}
