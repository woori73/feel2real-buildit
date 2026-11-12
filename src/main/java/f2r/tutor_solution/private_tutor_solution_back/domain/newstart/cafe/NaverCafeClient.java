package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 공식 호스트 두 개만 호출. 임의 URL, 쿠키, 비밀번호, 브라우저 세션은 받지 않는다. */
@Component
public class NaverCafeClient {
  private final ObjectMapper json;
  private final HttpClient http;
  private final CafeProperties properties;

  @org.springframework.beans.factory.annotation.Autowired
  public NaverCafeClient(ObjectMapper json, CafeProperties properties) {
    this(json, properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build());
  }

  NaverCafeClient(ObjectMapper json, CafeProperties properties, HttpClient http) {
    this.json = json; this.properties = properties; this.http = http;
  }

  public record Tokens(String access, String refresh, long expiresIn) {
    @Override public String toString() { return "Tokens[redacted]"; }
  }
  public record Published(long articleId, String articleUrl) {}

  public String authorizationUrl(String state) {
    return "https://nid.naver.com/oauth2.0/authorize?" + form(Map.of(
        "response_type", "code", "client_id", properties.getClientId(),
        "redirect_uri", properties.callback(), "state", state));
  }

  public Tokens exchange(String code, String state) {
    return tokens(Map.of("grant_type", "authorization_code", "code", code, "state", state));
  }

  public Tokens refresh(String refresh) {
    return tokens(Map.of("grant_type", "refresh_token", "refresh_token", refresh));
  }

  private Tokens tokens(Map<String, String> fields) {
    var values = new LinkedHashMap<>(fields);
    values.put("client_id", properties.getClientId());
    values.put("client_secret", properties.getClientSecret());
    var request = HttpRequest.newBuilder(URI.create("https://nid.naver.com/oauth2.0/token"))
        .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form(values))).build();
    JsonNode body;
    try { body = send(request); }
    catch (RuntimeException e) { throw CafeService.error(502, "naver_auth_failed_reconnect"); }
    String access = body.path("access_token").asText("");
    long expiry = body.path("expires_in").asLong(0);
    if (access.isBlank() || access.length() > 8192 || expiry < 1 || expiry > 864000)
      throw CafeService.error(502, "naver_auth_failed_reconnect");
    return new Tokens(access, body.path("refresh_token").asText(""), expiry);
  }

  public Published publish(String access, int menuId, String title, String content) {
    String body = "subject=" + cafeEncode(title) + "&content=" + cafeEncode(content)
        + "&openyn=false&searchopen=false&scrapyn=false&metoo=false&ccl=false";
    var request = HttpRequest.newBuilder(URI.create("https://openapi.naver.com/v1/cafe/"
        + CafeService.CAFE_ID + "/menu/" + menuId + "/articles"))
        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + access)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.US_ASCII)).build();
    return published(send(request));
  }

  public Published publish(String access, int menuId, String title, String content, List<CafeImage> images) {
    var checked = CafeImage.validate(images);
    if (checked.isEmpty()) return publish(access, menuId, title, content);
    String boundary = "f2r-cafe-" + UUID.randomUUID();
    var parts = new ArrayList<byte[]>();
    // multipart는 form-urlencoded의 바깥 URL decode가 없으므로 UTF-8 URL encode 한 번만 한다.
    var fields = new LinkedHashMap<String, String>();
    fields.put("subject", URLEncoder.encode(title, StandardCharsets.UTF_8));
    fields.put("content", URLEncoder.encode(content, StandardCharsets.UTF_8));
    for (String field : List.of("openyn", "searchopen", "scrapyn", "metoo", "ccl")) fields.put(field, "false");
    fields.forEach((name, value) -> parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
        + name + "\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.US_ASCII)));
    int index = 1;
    for (var image : checked) {
      String extension = image.mimeType().equals("image/png") ? "png" : "jpg";
      parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image-"
          + index++ + "." + extension + "\"\r\nContent-Type: " + image.mimeType()
          + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      parts.add(image.bytes());
      parts.add("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
    parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    var request = HttpRequest.newBuilder(URI.create("https://openapi.naver.com/v1/cafe/"
        + CafeService.CAFE_ID + "/menu/" + menuId + "/articles"))
        .timeout(Duration.ofSeconds(40)).header("Authorization", "Bearer " + access)
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArrays(parts)).build();
    return published(send(request));
  }

  private Published published(JsonNode result) {
    // 성공 응답이 불완전해도 재전송하지 않는다. 이미 게시됐을 수 있기 때문이다.
    long id = result.path("message").path("result").path("articleId").asLong(0);
    if (id == 0) id = result.path("articleId").asLong(0);
    if (id <= 0) throw CafeService.error(502, "cafe_publish_result_unknown");
    return new Published(id, "https://cafe.naver.com/ca-fe/cafes/" + CafeService.CAFE_ID + "/articles/" + id);
  }

  private JsonNode send(HttpRequest request) {
    try {
      // InputStream으로 상한을 적용해 오류 HTML/대형 응답을 메모리에 무제한 적재하지 않는다.
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var in = response.body()) {
        byte[] bytes = in.readNBytes(65537);
        if (response.statusCode() != 200 || bytes.length > 65536)
          throw CafeService.error(502, "naver_request_failed_or_unknown");
        return json.readTree(bytes);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw CafeService.error(502, "naver_request_failed_or_unknown");
    } catch (IOException e) {
      throw CafeService.error(502, "naver_request_failed_or_unknown");
    }
  }

  static String cafeEncode(String value) {
    return URLEncoder.encode(URLEncoder.encode(value, StandardCharsets.UTF_8), Charset.forName("MS949"));
  }

  static String form(Map<String, String> values) {
    return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(java.util.stream.Collectors.joining("&"));
  }
}
