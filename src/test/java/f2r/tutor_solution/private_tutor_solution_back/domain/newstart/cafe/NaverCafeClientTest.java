package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NaverCafeClientTest {
  @SuppressWarnings("unchecked")
  HttpClient responding(int status, String body) throws Exception {
    var http = mock(HttpClient.class);
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenAnswer(ignored -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(response);
    return http;
  }

  @Test
  void givenOfficialSuccess_whenPublishing_thenUsesFixedCafeAndReturnsCanonicalUrl() throws Exception {
    var http = responding(200, "{\"message\":{\"result\":{\"articleId\":987}}}");
    var client = new NaverCafeClient(JsonMapper.builder().build(), new CafeProperties(), http);
    var result = client.publish("synthetic-access", 101, "한글 제목", "<p>본문</p>");
    assertEquals(987, result.articleId());
    assertEquals("https://cafe.naver.com/ca-fe/cafes/31312978/articles/987", result.articleUrl());
    var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(captor.capture(), any());
    assertEquals("https://openapi.naver.com/v1/cafe/31312978/menu/101/articles", captor.getValue().uri().toString());
    String body = body(captor.getValue());
    assertTrue(body.contains("subject=" + NaverCafeClient.cafeEncode("한글 제목")));
    assertTrue(body.contains("openyn=false&searchopen=false"));
    assertFalse(captor.getValue().uri().toString().contains("synthetic-access"));
  }

  @Test
  void givenTwoImages_whenPublishing_thenSendsMultipartBinaryInOrderAndKoreanEncodedOnce() throws Exception {
    var http = responding(200, "{\"articleId\":456}");
    var client = new NaverCafeClient(JsonMapper.builder().build(), new CafeProperties(), http);
    assertEquals(456, client.publish("synthetic", 31, "한글 제목", "<p>본문</p>",
        java.util.List.of(CafeImageTest.image(0), CafeImageTest.image(1))).articleId());
    var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(captor.capture(), any());
    var request = captor.getValue(); var body = body(request);
    assertTrue(request.headers().firstValue("Content-Type").orElseThrow().startsWith("multipart/form-data; boundary="));
    assertTrue(body.contains(java.net.URLEncoder.encode("한글 제목", StandardCharsets.UTF_8)));
    assertFalse(body.contains(NaverCafeClient.cafeEncode("한글 제목")));
    assertTrue(body.contains("name=\"image\"; filename=\"image-1.png\""));
    assertTrue(body.indexOf("image-1.png") < body.indexOf("image-2.png"));
    assertTrue(body.contains("name=\"openyn\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nfalse"));
    assertTrue(body.contains("name=\"searchopen\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nfalse"));
    assertTrue(new String(bodyBytes(request), StandardCharsets.ISO_8859_1).contains(
        new String(CafeImageTest.image(0).bytes(), StandardCharsets.ISO_8859_1)));
  }

  @Test
  void givenErrorOrMissingArticleId_whenPublishing_thenNeverReportsSuccess() throws Exception {
    for (var http : new HttpClient[]{responding(403, "{\"error\":\"denied\"}"), responding(200, "{}")}) {
      var client = new NaverCafeClient(JsonMapper.builder().build(), new CafeProperties(), http);
      assertThrows(RuntimeException.class, () -> client.publish("synthetic", 101, "제목", "내용"));
      verify(http, times(1)).send(any(), any());
    }
  }

  @Test
  void givenTokenRequest_whenExchanging_thenSecretsUsePostBodyAndNotUrl() throws Exception {
    var http = responding(200, "{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":\"3600\"}");
    var config = new CafeProperties(); config.setClientId("synthetic-id"); config.setClientSecret("synthetic-secret");
    var client = new NaverCafeClient(JsonMapper.builder().build(), config, http);
    assertEquals(3600, client.exchange("synthetic-code", "synthetic-state").expiresIn());
    var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(captor.capture(), any());
    assertEquals("https://nid.naver.com/oauth2.0/token", captor.getValue().uri().toString());
    assertEquals("POST", captor.getValue().method());
    assertTrue(body(captor.getValue()).contains("client_secret=synthetic-secret"));
  }

  static String body(HttpRequest request) {
    return new String(bodyBytes(request), StandardCharsets.UTF_8);
  }

  static byte[] bodyBytes(HttpRequest request) {
    var bytes = new ByteArrayOutputStream(); var done = new CompletableFuture<Void>();
    request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
      public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
      public void onNext(ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
      public void onError(Throwable error) { done.completeExceptionally(error); }
      public void onComplete() { done.complete(null); }
    });
    done.join(); return bytes.toByteArray();
  }
}
