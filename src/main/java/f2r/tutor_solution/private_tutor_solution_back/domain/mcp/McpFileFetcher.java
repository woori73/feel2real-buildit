package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ChatGPT 등 MCP 호스트가 도구 인자로 넣어 주는 파일 객체({@code openai/fileParams}의 download_url)를 서버가 직접 받는다.
 *
 * <p>모델이 base64를 글자로 써서 넘기지 않아도 되므로 큰 이미지·영상도 올라간다. SSRF를 막기 위해:
 *
 * <ul>
 *   <li>https만, 첫 주소의 호스트는 {@code app.mcp.file-param-hosts} 접미사 목록 안이어야 한다
 *   <li>모든 hop(리다이렉트 포함, 최대 3번)의 주소가 공인 IP여야 한다(루프백·사설·링크로컬·멀티캐스트 거부)
 *   <li>받는 크기는 {@code maxBytes}(기본 100 MiB)로 자르고, 응답 대기는 120초까지
 * </ul>
 */
@Component
public class McpFileFetcher {
  /** DNS 조회를 테스트에서 바꿀 수 있게 분리했다. */
  interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  public record Fetched(InputStream body, String contentType) {}

  private static final int MAX_REDIRECTS = 3;

  private final List<String> allowedHostSuffixes;
  private final long maxBytes;
  private final Resolver resolver;
  private final HttpClient http;

  @Autowired
  public McpFileFetcher(
      @Value("${app.mcp.file-param-hosts:oaiusercontent.com,openai.com}") String hosts,
      @Value("${app.mcp.file-param-max-bytes:104857600}") long maxBytes) {
    this(hosts, maxBytes, InetAddress::getAllByName, defaultClient());
  }

  McpFileFetcher(String hosts, long maxBytes, Resolver resolver, HttpClient http) {
    this.allowedHostSuffixes =
        Arrays.stream(hosts.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .toList();
    this.maxBytes = maxBytes;
    this.resolver = resolver;
    this.http = http;
  }

  private static HttpClient defaultClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public Fetched fetch(String downloadUrl) throws IOException, InterruptedException {
    URI uri = parse(downloadUrl);
    if (!allowedHost(uri.getHost())) throw McpOAuth.error(400, "file_host_not_allowed");
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      requirePublic(uri.getHost());
      var response =
          http.send(
              HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
      int status = response.statusCode();
      if (status >= 300 && status < 400) {
        response.body().close();
        String location = response.headers().firstValue("location").orElse(null);
        if (location == null) throw McpOAuth.error(502, "file_redirect_without_location");
        uri = parse(uri.resolve(location).toString());
        continue;
      }
      if (status != 200) {
        response.body().close();
        throw McpOAuth.error(502, "file_download_failed_" + status);
      }
      long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
      if (declared > maxBytes) {
        response.body().close();
        throw McpOAuth.error(413, "file_too_large");
      }
      return new Fetched(
          new Capped(response.body(), maxBytes),
          response.headers().firstValue("content-type").orElse(""));
    }
    throw McpOAuth.error(502, "file_too_many_redirects");
  }

  private static URI parse(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      throw McpOAuth.error(400, "invalid_file_url");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
      throw McpOAuth.error(400, "invalid_file_url");
    return uri;
  }

  boolean allowedHost(String host) {
    String h = host.toLowerCase(Locale.ROOT);
    return allowedHostSuffixes.stream().anyMatch(s -> h.equals(s) || h.endsWith("." + s));
  }

  void requirePublic(String host) throws UnknownHostException {
    for (InetAddress a : resolver.resolve(host)) {
      boolean ula = a instanceof Inet6Address && (a.getAddress()[0] & 0xfe) == 0xfc;
      if (a.isAnyLocalAddress()
          || a.isLoopbackAddress()
          || a.isSiteLocalAddress()
          || a.isLinkLocalAddress()
          || a.isMulticastAddress()
          || ula
          || isCarrierGradeNat(a)) throw McpOAuth.error(400, "file_host_not_public");
    }
  }

  private static boolean isCarrierGradeNat(InetAddress a) {
    byte[] b = a.getAddress();
    return b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
  }

  /** 선언 길이가 없거나 틀려도 maxBytes를 넘으면 읽기를 멈춘다. */
  static final class Capped extends FilterInputStream {
    private final long max;
    private long total;

    Capped(InputStream in, long max) {
      super(in);
      this.max = max;
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b != -1 && ++total > max) throw new IOException("file_too_large");
      return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      int n = super.read(buf, off, len);
      if (n > 0 && (total += n) > max) throw new IOException("file_too_large");
      return n;
    }
  }
}
