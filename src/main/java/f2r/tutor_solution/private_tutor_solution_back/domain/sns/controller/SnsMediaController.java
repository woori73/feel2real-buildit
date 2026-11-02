package f2r.tutor_solution.private_tutor_solution_back.domain.sns.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsMediaService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시물 이미지 공개 주소. 인스타그램 서버가 image_url로 가져가야 하므로 로그인 없이 열지만, 주소에는 추측할 수 없는
 * 임의 토큰(24바이트)만 들어가고 버린 게시물은 내주지 않는다. 검색엔진 수집은 막는다.
 */
@RestController
public class SnsMediaController {

  private final SnsMediaService media;

  public SnsMediaController(SnsMediaService media) {
    this.media = media;
  }

  @GetMapping("/api/sns/media/{token}.jpg")
  public ResponseEntity<byte[]> image(@PathVariable String token) {
    if (!token.matches("[A-Za-z0-9_-]{16,64}")) return ResponseEntity.notFound().build();
    return media.postImageByToken(token)
        .map(m -> ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .header("X-Robots-Tag", "noindex, nofollow")
            .body(m.bytes()))
        .orElse(ResponseEntity.notFound().build());
  }
}
