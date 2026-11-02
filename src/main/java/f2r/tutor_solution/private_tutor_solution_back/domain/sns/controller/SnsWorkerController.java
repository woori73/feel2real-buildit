package f2r.tutor_solution.private_tutor_solution_back.domain.sns.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto.SnsDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsMediaService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * GPU 작업자(runyour.ai 인스턴스에서 도는 gpu-pipeline/sns_worker) 전용 API. 운영 DB·자격증명은 GPU에 주지 않고,
 * 작업자는 이 API로 작업을 가져가고 결과 JPEG만 올린다. 인증: Authorization: Bearer {SNS_WORKER_TOKEN}.
 */
@RestController
@RequestMapping("/api/sns/worker")
public class SnsWorkerController {

  private final SnsProperties properties;
  private final SnsGenerationService generation;
  private final SnsMediaService media;
  private final SnsPostRepository posts;

  public SnsWorkerController(SnsProperties properties, SnsGenerationService generation, SnsMediaService media, SnsPostRepository posts) {
    this.properties = properties;
    this.generation = generation;
    this.media = media;
    this.posts = posts;
  }

  public record FailRequest(String reason, Long durationMs) {}

  /** 다음 작업. 없으면 204. */
  @PostMapping("/jobs/next")
  public ResponseEntity<?> next(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
    ResponseEntity<SnsDtos.Message> denied = authorize(auth);
    if (denied != null) return denied;
    return generation.claimNextImageJob()
        .<ResponseEntity<?>>map(job -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("jobId", job.postId());
          body.put("ruleId", job.ruleId());
          body.put("attempt", job.attempt());
          body.put("prompt", job.prompt());
          body.put("aspectRatio", job.aspectRatio());
          body.put("referenceImageUrl", job.referenceImageId() == null ? null : "/api/sns/worker/jobs/" + job.postId() + "/reference");
          return ResponseEntity.ok(body);
        })
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/jobs/{id}/reference")
  public ResponseEntity<?> reference(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @PathVariable Long id) {
    ResponseEntity<SnsDtos.Message> denied = authorize(auth);
    if (denied != null) return denied;
    SnsPost post = posts.findById(id).orElse(null);
    if (post == null || post.getImageStatus() != SnsPost.PartStatus.RUNNING) return ResponseEntity.notFound().build();
    return generation.referenceImageIdFor(post)
        .flatMap(media::approvedReferenceImage)
        .<ResponseEntity<?>>map(m -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(m.contentType())).body(m.bytes()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping(value = "/jobs/{id}/result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> result(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable Long id, @RequestParam MultipartFile image, @RequestParam long durationMs,
      @RequestParam(required = false) String model) {
    ResponseEntity<SnsDtos.Message> denied = authorize(auth);
    if (denied != null) return denied;
    try {
      String key = media.saveGeneratedJpeg(id, image.getBytes());
      generation.completeImage(id, key, durationMs, model == null ? "comfyui" : model.length() > 100 ? model.substring(0, 100) : model);
      return ResponseEntity.ok(new SnsDtos.Message("저장했습니다."));
    } catch (IOException e) {
      generation.failImage(id, "결과 이미지 오류: " + e.getMessage(), durationMs);
      return ResponseEntity.badRequest().body(new SnsDtos.Message(e.getMessage()));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new SnsDtos.Message(e.getMessage()));
    }
  }

  @PostMapping("/jobs/{id}/fail")
  public ResponseEntity<?> fail(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable Long id, @RequestBody FailRequest req) {
    ResponseEntity<SnsDtos.Message> denied = authorize(auth);
    if (denied != null) return denied;
    try {
      String reason = req.reason() == null ? null : req.reason().length() > 300 ? req.reason().substring(0, 300) : req.reason();
      generation.failImage(id, reason, req.durationMs() == null ? 0 : req.durationMs());
      return ResponseEntity.ok(new SnsDtos.Message("기록했습니다."));
    } catch (IllegalStateException | IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new SnsDtos.Message(e.getMessage()));
    }
  }

  /** null이면 통과. 토큰 미설정 503, 불일치 401. 비교는 일정 시간 비교. */
  ResponseEntity<SnsDtos.Message> authorize(String header) {
    String expected = properties.getWorkerToken();
    if (expected == null || expected.isBlank()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new SnsDtos.Message("GPU 작업자 토큰이 설정되지 않았습니다."));
    }
    String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
    boolean ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    return ok ? null : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SnsDtos.Message("인증 실패"));
  }
}
