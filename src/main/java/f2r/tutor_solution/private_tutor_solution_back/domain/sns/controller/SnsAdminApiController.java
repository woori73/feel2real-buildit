package f2r.tutor_solution.private_tutor_solution_back.domain.sns.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto.SnsDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram.InstagramApi;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsCaptionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsMediaService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsPublishService;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.service.SnsTokenCipher;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 인스타 자동 피드 관리자 API. /api/admin/** 는 SecurityConfig에서 ADMIN 전용이고, 여기서도 한 번 더 막는다. */
@RestController
@RequestMapping("/api/admin/sns")
@PreAuthorize("hasRole('ADMIN')")
public class SnsAdminApiController {

  static final String STATE_COOKIE = "sns_oauth_state";

  private final SnsAdminService admin;
  private final SnsGenerationService generation;
  private final SnsPublishService publish;
  private final SnsMediaService media;
  private final InstagramApi instagram;
  private final SnsTokenCipher cipher;
  private final SnsProperties properties;
  private final SecureRandom random = new SecureRandom();

  public SnsAdminApiController(SnsAdminService admin, SnsGenerationService generation, SnsPublishService publish,
      SnsMediaService media, InstagramApi instagram, SnsTokenCipher cipher, SnsProperties properties) {
    this.admin = admin;
    this.generation = generation;
    this.publish = publish;
    this.media = media;
    this.instagram = instagram;
    this.cipher = cipher;
    this.properties = properties;
  }

  @GetMapping("/summary")
  public SnsDtos.Summary summary() {
    return admin.summary();
  }

  @GetMapping("/logs")
  public List<SnsDtos.LogView> logs(@RequestParam(defaultValue = "50") int limit) {
    return admin.recentLogs(limit);
  }

  // ---- 계정 ----

  @GetMapping("/accounts")
  public List<SnsDtos.AccountView> accounts() {
    return admin.accounts();
  }

  /** 인스타그램 로그인 주소. state는 HttpOnly 쿠키로 묶어 콜백에서 대조한다. 로그인·동의는 계정 주인이 직접 한다. */
  @PostMapping("/accounts/connect")
  public ResponseEntity<Map<String, String>> connectUrl() {
    if (!properties.metaConfigured()) throw new IllegalStateException("Meta 앱 정보(SNS_META_APP_ID·SNS_META_APP_SECRET)가 설정되지 않았습니다.");
    if (!cipher.isConfigured()) throw new IllegalStateException("토큰 암호화 키(SNS_TOKEN_KEY_BASE64)가 설정되지 않았습니다.");
    byte[] b = new byte[24];
    random.nextBytes(b);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    ResponseCookie cookie = ResponseCookie.from(STATE_COOKIE, state).httpOnly(true)
        .secure(properties.getPublicBaseUrl().startsWith("https")).sameSite("Lax").path("/admin/sns").maxAge(Duration.ofMinutes(10)).build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(Map.of("url", instagram.authorizeUrl(state)));
  }

  @PostMapping("/accounts/{id}/refresh")
  public SnsDtos.AccountView refresh(@PathVariable Long id) {
    var a = admin.accounts().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    if (!"CONNECTED".equals(a.status())) throw new IllegalStateException("연결된 계정만 갱신할 수 있습니다.");
    publish.refreshExpiringOne(id);
    return admin.accounts().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
  }

  @PostMapping("/accounts/{id}/disconnect")
  public SnsDtos.AccountView disconnect(@PathVariable Long id) {
    publish.disconnect(id);
    return admin.accounts().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
  }

  @DeleteMapping("/accounts/{id}")
  public SnsDtos.Message deleteAccount(@PathVariable Long id) {
    admin.deleteAccount(id);
    return new SnsDtos.Message("삭제했습니다.");
  }

  // ---- 브랜드 ----

  @GetMapping("/brands")
  public List<SnsDtos.BrandView> brands() {
    return admin.brands();
  }

  @PostMapping("/brands")
  public SnsDtos.BrandView createBrand(@RequestBody SnsDtos.BrandRequest req) {
    return admin.saveBrand(null, req);
  }

  @PutMapping("/brands/{id}")
  public SnsDtos.BrandView updateBrand(@PathVariable Long id, @RequestBody SnsDtos.BrandRequest req) {
    return admin.saveBrand(id, req);
  }

  @DeleteMapping("/brands/{id}")
  public SnsDtos.Message deleteBrand(@PathVariable Long id) {
    admin.deleteBrand(id);
    return new SnsDtos.Message("삭제했습니다.");
  }

  @PostMapping("/brands/{id}/test-caption")
  public SnsDtos.CaptionPreview testCaption(@PathVariable Long id) {
    return admin.testCaption(id);
  }

  // ---- 참고 이미지 ----

  @GetMapping("/references")
  public List<SnsDtos.ReferenceView> references() {
    return admin.references();
  }

  @PostMapping(value = "/references", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SnsDtos.ReferenceView uploadReference(@RequestParam String label, @RequestParam(required = false) String brand,
      @RequestParam MultipartFile file) throws IOException {
    String key = media.saveReferenceJpeg(label, file.getBytes());
    // 새로 올린 얼굴은 바로 쓰지 않는다 — 관리자가 확인 후 승인.
    return admin.addReference(label, brand, key, false);
  }

  @PostMapping("/references/{id}/approve")
  public SnsDtos.ReferenceView approve(@PathVariable Long id, @RequestParam boolean approved) {
    return admin.setReferenceApproved(id, approved);
  }

  @DeleteMapping("/references/{id}")
  public SnsDtos.Message deleteReference(@PathVariable Long id) {
    admin.deleteReference(id);
    return new SnsDtos.Message("삭제했습니다.");
  }

  @GetMapping("/references/{id}/image")
  public ResponseEntity<byte[]> referenceImage(@PathVariable Long id) {
    return media.referenceImage(id)
        .map(m -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(m.contentType())).body(m.bytes()))
        .orElse(ResponseEntity.notFound().build());
  }

  // ---- 규칙 ----

  @GetMapping("/rules")
  public List<SnsDtos.RuleView> rules() {
    return admin.rules();
  }

  @PostMapping("/rules")
  public SnsDtos.RuleView createRule(@RequestBody SnsDtos.RuleRequest req) {
    return admin.saveRule(null, req);
  }

  @PutMapping("/rules/{id}")
  public SnsDtos.RuleView updateRule(@PathVariable Long id, @RequestBody SnsDtos.RuleRequest req) {
    return admin.saveRule(id, req);
  }

  @PostMapping("/rules/{id}/enabled")
  public SnsDtos.RuleView setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
    return admin.setRuleEnabled(id, enabled);
  }

  @DeleteMapping("/rules/{id}")
  public SnsDtos.Message deleteRule(@PathVariable Long id) {
    admin.deleteRule(id);
    return new SnsDtos.Message("삭제했습니다.");
  }

  /** 시험 생성: 이미지 1장 + 문구 1개. 결과는 규칙 화면의 시험 결과에 보인다. */
  @PostMapping("/rules/{id}/trial")
  public SnsDtos.PostView trial(@PathVariable Long id) {
    SnsPost p = generation.startRun(admin.rule(id), true);
    return admin.post(p.getId());
  }

  /** 즉시 실행: 예약과 별개로 지금 한 번(검토 대기 탭에 쌓임). */
  @PostMapping("/rules/{id}/run-now")
  public SnsDtos.PostView runNow(@PathVariable Long id) {
    SnsPost p = generation.startRun(admin.rule(id), false);
    return admin.post(p.getId());
  }

  @GetMapping("/rules/{id}/trials")
  public List<SnsDtos.PostView> trials(@PathVariable Long id) {
    return admin.trialPosts(id);
  }

  // ---- 게시물 ----

  @GetMapping("/posts")
  public List<SnsDtos.PostView> posts(@RequestParam(defaultValue = "review") String tab, @RequestParam(defaultValue = "50") int limit) {
    return admin.posts(tab, limit);
  }

  @GetMapping("/posts/{id}")
  public SnsDtos.PostView post(@PathVariable Long id) {
    return admin.post(id);
  }

  @PutMapping("/posts/{id}")
  public SnsDtos.PostView edit(@PathVariable Long id, @RequestBody SnsDtos.PostEditRequest req) {
    return admin.editPost(id, req);
  }

  @PostMapping("/posts/{id}/regenerate-image")
  public SnsDtos.PostView regenerate(@PathVariable Long id) {
    generation.regenerateImage(id);
    return admin.post(id);
  }

  @PostMapping("/posts/{id}/publish")
  public SnsDtos.PostView publishNow(@PathVariable Long id) {
    publish.publishNow(id);
    return admin.post(id);
  }

  @PostMapping("/posts/{id}/schedule")
  public SnsDtos.PostView schedule(@PathVariable Long id, @RequestBody SnsDtos.ScheduleRequest req) {
    publish.schedule(id, req.scheduledAt());
    return admin.post(id);
  }

  @PostMapping("/posts/{id}/unschedule")
  public SnsDtos.PostView unschedule(@PathVariable Long id) {
    publish.unschedule(id);
    return admin.post(id);
  }

  @PostMapping("/posts/{id}/discard")
  public SnsDtos.PostView discard(@PathVariable Long id) {
    return admin.discard(id);
  }

  // ---- 오류 → 사람이 읽을 메시지 ----

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<SnsDtos.Message> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new SnsDtos.Message(e.getMessage()));
  }

  @ExceptionHandler({IllegalStateException.class, SnsGenerationService.GenerationBlocked.class,
      SnsCaptionService.CaptionRejected.class, InstagramApi.InstagramApiException.class})
  public ResponseEntity<SnsDtos.Message> conflict(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new SnsDtos.Message(e.getMessage()));
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<SnsDtos.Message> io(IOException e) {
    return ResponseEntity.badRequest().body(new SnsDtos.Message(e.getMessage()));
  }
}
