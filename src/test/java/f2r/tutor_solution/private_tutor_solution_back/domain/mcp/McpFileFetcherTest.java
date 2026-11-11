package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 가짜 주소·가짜 응답만 사용한다(실제 네트워크 없음). */
class McpFileFetcherTest {
  private static final String HOSTS = "oaiusercontent.com";

  private static InetAddress ip(String v) throws Exception {
    return InetAddress.getByName(v); // 숫자 주소는 DNS 조회 없이 만들어진다
  }

  private static McpFileFetcher.Resolver resolver(Map<String, String> table) {
    return host -> {
      try {
        return new InetAddress[] {ip(table.getOrDefault(host, "203.0.113.10"))};
      } catch (Exception e) {
        throw new java.net.UnknownHostException(host);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<InputStream> response(int status, Map<String, String> headers, byte[] body) {
    HttpResponse<InputStream> r = mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(status);
    when(r.headers())
        .thenReturn(
            HttpHeaders.of(
                headers.entrySet().stream()
                    .collect(
                        java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue()))),
                (a, b) -> true));
    when(r.body()).thenReturn(new ByteArrayInputStream(body));
    return r;
  }

  private static int status(ResponseStatusException e) {
    return e.getStatusCode().value();
  }

  @Test
  void givenHttpOrForeignHost_whenFetch_thenRejectedBeforeAnyRequest() {
    // given
    HttpClient http = mock(HttpClient.class);
    var fetcher = new McpFileFetcher(HOSTS, 1000, resolver(Map.of()), http);
    // when
    var plain =
        assertThrows(ResponseStatusException.class, () -> fetcher.fetch("http://files.oaiusercontent.com/a"));
    var foreign =
        assertThrows(ResponseStatusException.class, () -> fetcher.fetch("https://evil.example.com/a"));
    var lookalike =
        assertThrows(ResponseStatusException.class, () -> fetcher.fetch("https://evil-oaiusercontent.com/a"));
    // then
    assertEquals(400, status(plain));
    assertEquals("file_host_not_allowed", foreign.getReason());
    assertEquals("file_host_not_allowed", lookalike.getReason());
    verifyNoInteractions(http);
  }

  @Test
  void givenAllowedHostResolvingToPrivateAddress_whenFetch_thenRejected() {
    // given
    HttpClient http = mock(HttpClient.class);
    var fetcher =
        new McpFileFetcher(HOSTS, 1000, resolver(Map.of("files.oaiusercontent.com", "10.0.0.5")), http);
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> fetcher.fetch("https://files.oaiusercontent.com/a"));
    // then
    assertEquals("file_host_not_public", e.getReason());
    verifyNoInteractions(http);
  }

  @Test
  void givenRedirectToLoopback_whenFetch_thenRejected() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    var redirect = response(302, Map.of("location", "https://inner.example.net/x"), new byte[0]);
    doReturn(redirect).when(http).send(any(), any());
    var fetcher =
        new McpFileFetcher(HOSTS, 1000, resolver(Map.of("inner.example.net", "127.0.0.1")), http);
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> fetcher.fetch("https://files.oaiusercontent.com/a"));
    // then
    assertEquals("file_host_not_public", e.getReason());
    verify(http, times(1)).send(any(), any());
  }

  @Test
  void givenPublicAllowedHost_whenFetch_thenBodyStreamed() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    doReturn(response(200, Map.of("content-type", "image/png"), new byte[] {1, 2, 3}))
        .when(http)
        .send(any(), any());
    var fetcher = new McpFileFetcher(HOSTS, 1000, resolver(Map.of()), http);
    // when
    var fetched = fetcher.fetch("https://files.oaiusercontent.com/a?sig=1");
    // then
    assertArrayEquals(new byte[] {1, 2, 3}, fetched.body().readAllBytes());
    assertEquals("image/png", fetched.contentType());
  }

  @Test
  void givenDeclaredLengthOverLimit_whenFetch_thenTooLarge() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    doReturn(response(200, Map.of("content-length", "5000"), new byte[0])).when(http).send(any(), any());
    var fetcher = new McpFileFetcher(HOSTS, 1000, resolver(Map.of()), http);
    // when
    var e =
        assertThrows(
            ResponseStatusException.class, () -> fetcher.fetch("https://files.oaiusercontent.com/a"));
    // then
    assertEquals(413, status(e));
  }

  @Test
  void givenBodyLongerThanLimit_whenRead_thenStopsWithError() {
    // given
    var capped = new McpFileFetcher.Capped(new ByteArrayInputStream(new byte[20]), 10);
    // when / then
    assertThrows(IOException.class, capped::readAllBytes);
  }
}
