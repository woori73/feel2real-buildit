package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import java.net.URI;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고1 10월 영어 복습 게임(Unity WebGL)을 Feel2Real 안에서 실행한다.
 *
 * <p>빌드 4개 파일은 brotli로 미리 압축해 classpath {@code games/}(정적 경로 밖)에 둔다. 정해진 파일 이름만
 * 응답하고, 버전 경로(v1)라서 긴 캐시를 준다. 게임 페이지는 같은 출처의 파일만 쓰도록 CSP로 묶는다.
 */
@RestController
public class HomeReviewGameController {

  public static final String PAGE_PATH = "/games/g1-oct-review/v1/";
  private static final String ROOT = "games/g1-oct-review/v1/";

  // Unity WebGL needs WebAssembly compilation; everything else stays on this origin.
  static final String PAGE_CSP = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval' 'unsafe-eval'; "
      + "style-src 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; "
      + "connect-src 'self' blob: data:; worker-src 'self' blob:; frame-ancestors 'self'; "
      + "base-uri 'none'; form-action 'none'; object-src 'none'";

  static final Map<String, MediaType> BUILD_FILES = Map.of(
      "WebGLBuild.loader.js", MediaType.parseMediaType("text/javascript"),
      "WebGLBuild.framework.js", MediaType.parseMediaType("text/javascript"),
      "WebGLBuild.data", MediaType.APPLICATION_OCTET_STREAM,
      "WebGLBuild.wasm", MediaType.parseMediaType("application/wasm"));

  /** 상대 경로(Build/…)가 맞게 풀리도록 끝 슬래시 주소로 보낸다. */
  @GetMapping("/games/g1-oct-review/v1")
  public ResponseEntity<Void> pageWithoutSlash() {
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(PAGE_PATH)).build();
  }

  @GetMapping({PAGE_PATH, PAGE_PATH + "index.html"})
  public ResponseEntity<Resource> page() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .cacheControl(CacheControl.noCache())
        .header("Content-Security-Policy", PAGE_CSP)
        .header("X-Content-Type-Options", "nosniff")
        .body(new ClassPathResource(ROOT + "index.html"));
  }

  @GetMapping(PAGE_PATH + "game.js")
  public ResponseEntity<Resource> starter() {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/javascript;charset=UTF-8"))
        .cacheControl(CacheControl.noCache())
        .header("X-Content-Type-Options", "nosniff")
        .body(new ClassPathResource(ROOT + "game.js"));
  }

  @GetMapping(PAGE_PATH + "Build/{file}")
  public ResponseEntity<?> build(@PathVariable("file") String file,
      @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding) {
    MediaType type = BUILD_FILES.get(file);
    if (type == null) return ResponseEntity.notFound().build();
    if (!acceptsBrotli(acceptEncoding)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
          .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
          .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING)
          .body("이 브라우저는 게임 파일 형식(brotli)을 지원하지 않습니다.");
    }
    return ResponseEntity.ok()
        .contentType(type)
        .header(HttpHeaders.CONTENT_ENCODING, "br")
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING)
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
        .body(new ClassPathResource(ROOT + "Build/" + file + ".br"));
  }

  /** Accept-Encoding에 br이 있고 q=0으로 거절하지 않았으면 true. */
  static boolean acceptsBrotli(String acceptEncoding) {
    if (acceptEncoding == null) return false;
    for (String part : acceptEncoding.split(",")) {
      String[] fields = part.trim().split(";");
      if (!fields[0].trim().equalsIgnoreCase("br")) continue;
      for (int i = 1; i < fields.length; i++) {
        String param = fields[i].trim().replace(" ", "");
        if (param.matches("(?i)q=0(\\.0{0,3})?")) return false;
      }
      return true;
    }
    return false;
  }
}
