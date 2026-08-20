package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildItWikiProxyController {
  private final HttpClient client = HttpClient.newHttpClient();

  @RequestMapping("/problem-generator/wiki/api/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws Exception {
    String prefix = "/problem-generator/wiki";
    String targetPath = request.getRequestURI().substring(prefix.length());
    String query = request.getQueryString();
    URI uri = URI.create("http://127.0.0.1:8000" + targetPath + (query == null ? "" : "?" + query));
    byte[] body = request.getInputStream().readAllBytes();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    String contentType = request.getHeader("Content-Type");
    if (contentType != null) builder.header("Content-Type", contentType);
    HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    ResponseEntity.BodyBuilder out = ResponseEntity.status(response.statusCode());
    response.headers().firstValue("content-type").ifPresent(v -> out.header(HttpHeaders.CONTENT_TYPE, v));
    response.headers().firstValue("content-disposition").ifPresent(v -> out.header(HttpHeaders.CONTENT_DISPOSITION, v));
    return out.body(response.body());
  }
}
