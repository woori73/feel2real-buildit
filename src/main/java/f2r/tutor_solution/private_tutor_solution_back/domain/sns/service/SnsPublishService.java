package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram.InstagramApi;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import java.time.LocalDateTime;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 검토가 끝난 게시물을 인스타그램에 올린다(수동 발행·예약 발행). 실패하면 1회만 다시 시도하고 실패 탭에 사유를 남긴다.
 * 계정 연결(OAuth)·토큰 갱신도 여기서 한다. 토큰은 복호화한 값을 메모리에서만 쓰고 로그·응답에 싣지 않는다.
 */
@Service
public class SnsPublishService {

  private static final Logger log = LoggerFactory.getLogger(SnsPublishService.class);
  /** 컨테이너 준비 확인: 최대 횟수 × 간격. */
  static final int STATUS_POLLS = 10;
  static final long STATUS_POLL_MILLIS = 3000;
  /** 만료까지 이만큼 남으면 갱신한다. */
  public static final int REFRESH_BEFORE_DAYS = 10;
  /** 화면 경고 기준. */
  public static final int WARN_BEFORE_DAYS = 7;

  private final SnsPostRepository posts;
  private final SnsAutomationRuleRepository rules;
  private final SnsAccountRepository accounts;
  private final SnsGenerationLogRepository logs;
  private final InstagramApi instagram;
  private final SnsTokenCipher cipher;
  private final SnsProperties properties;
  private final LongConsumer sleeper;

  @Autowired
  public SnsPublishService(SnsPostRepository posts, SnsAutomationRuleRepository rules, SnsAccountRepository accounts,
      SnsGenerationLogRepository logs, InstagramApi instagram, SnsTokenCipher cipher, SnsProperties properties) {
    this(posts, rules, accounts, logs, instagram, cipher, properties, SnsPublishService::sleep);
  }

  SnsPublishService(SnsPostRepository posts, SnsAutomationRuleRepository rules, SnsAccountRepository accounts,
      SnsGenerationLogRepository logs, InstagramApi instagram, SnsTokenCipher cipher, SnsProperties properties,
      LongConsumer sleeper) {
    this.posts = posts;
    this.rules = rules;
    this.accounts = accounts;
    this.logs = logs;
    this.instagram = instagram;
    this.cipher = cipher;
    this.properties = properties;
    this.sleeper = sleeper;
  }

  public String mediaUrl(SnsPost post) {
    return SnsProperties.trimSlash(properties.getPublicBaseUrl()) + "/api/sns/media/" + post.getMediaToken() + ".jpg";
  }

  /** 지금 올린다. 성공하면 PUBLISHED + ig_media_id, 두 번 실패하면 FAILED. */
  public SnsPost publishNow(Long postId) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getStatus() != SnsPost.Status.REVIEW && post.getStatus() != SnsPost.Status.SCHEDULED) {
      throw new IllegalStateException("검토 대기 또는 예약 상태의 게시물만 발행할 수 있습니다.");
    }
    if (post.getMediaToken() == null || post.firstImagePath() == null) throw new IllegalStateException("이미지가 없는 게시물은 발행할 수 없습니다.");
    SnsAutomationRule rule = rules.findById(post.getRuleId()).orElseThrow(() -> new IllegalStateException("규칙이 삭제되었습니다."));
    SnsAccount account = rule.getAccountId() == null ? null : accounts.findById(rule.getAccountId()).orElse(null);
    if (account == null || account.getStatus() != SnsAccount.Status.CONNECTED) {
      throw new IllegalStateException("규칙에 연결된 인스타그램 계정이 없거나 연결이 끊겼습니다. 계정 화면에서 연결해 주세요.");
    }
    String token = cipher.decrypt(account.getAccessTokenEnc());
    String text = post.publishText();
    if (text.length() > SnsCaptionService.MAX_CAPTION_CHARS) throw new IllegalStateException("문구가 2,200자를 넘습니다.");
    String lastError = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      post.setPublishAttempts(post.getPublishAttempts() + 1);
      long t0 = System.currentTimeMillis();
      try {
        String containerId = instagram.createImageContainer(account.getIgUserId(), token, mediaUrl(post), text);
        waitUntilReady(containerId, token);
        String mediaId = instagram.publish(account.getIgUserId(), token, containerId);
        logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.PUBLISH, rule.getId(), post.getId(), "instagram", null, 0,
            System.currentTimeMillis() - t0, true, null));
        post.setIgMediaId(mediaId);
        post.setPublishedAt(SnsTime.now());
        post.setStatus(SnsPost.Status.PUBLISHED);
        post.setFailReason(null);
        return posts.save(post);
      } catch (RuntimeException e) {
        lastError = e instanceof InstagramApi.InstagramApiException || e instanceof IllegalStateException ? e.getMessage() : "발행 중 오류";
        logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.PUBLISH, rule.getId(), post.getId(), "instagram", null, 0,
            System.currentTimeMillis() - t0, false, lastError));
        log.warn("[sns] 발행 실패 post={} attempt={}: {}", post.getId(), attempt, lastError);
      }
    }
    post.fail("발행 실패(2회): " + lastError);
    return posts.save(post);
  }

  private void waitUntilReady(String containerId, String token) {
    for (int i = 0; i < STATUS_POLLS; i++) {
      String status = instagram.containerStatus(containerId, token);
      if ("FINISHED".equals(status) || "PUBLISHED".equals(status)) return;
      if ("ERROR".equals(status) || "EXPIRED".equals(status)) throw new IllegalStateException("인스타그램이 이미지를 처리하지 못했습니다(" + status + ").");
      sleeper.accept(STATUS_POLL_MILLIS);
    }
    throw new IllegalStateException("인스타그램 이미지 준비가 시간 안에 끝나지 않았습니다.");
  }

  /** 예약 발행 시각을 정한다(서울 시간). */
  public SnsPost schedule(Long postId, LocalDateTime at) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getStatus() != SnsPost.Status.REVIEW && post.getStatus() != SnsPost.Status.SCHEDULED) {
      throw new IllegalStateException("검토 대기 게시물만 예약할 수 있습니다.");
    }
    if (post.isTrial()) throw new IllegalStateException("시험 생성 결과는 예약할 수 없습니다. 바로 발행하거나 버려 주세요.");
    if (at == null || !at.isAfter(SnsTime.now())) throw new IllegalArgumentException("예약 시각은 지금 이후여야 합니다.");
    post.setScheduledAt(at);
    post.setStatus(SnsPost.Status.SCHEDULED);
    return posts.save(post);
  }

  public SnsPost unschedule(Long postId) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getStatus() != SnsPost.Status.SCHEDULED) throw new IllegalStateException("예약 상태가 아닙니다.");
    post.setScheduledAt(null);
    post.setStatus(SnsPost.Status.REVIEW);
    return posts.save(post);
  }

  public int publishDueScheduled() {
    int n = 0;
    for (SnsPost p : posts.dueScheduled(SnsTime.now())) {
      try {
        publishNow(p.getId());
      } catch (RuntimeException e) {
        // 계정 미연결 등 발행 전 단계 실패 → 실패 탭에 사유.
        SnsPost fresh = posts.findById(p.getId()).orElse(null);
        if (fresh != null && fresh.getStatus() == SnsPost.Status.SCHEDULED) {
          fresh.fail("예약 발행 실패: " + e.getMessage());
          posts.save(fresh);
        }
      }
      n++;
    }
    return n;
  }

  // ---- 계정 연결 ----

  /** OAuth 돌아온 code로 계정을 연결(이미 있으면 토큰 교체). */
  public SnsAccount connect(String code) {
    if (!cipher.isConfigured()) throw new IllegalStateException("토큰 암호화 키(SNS_TOKEN_KEY_BASE64)가 설정되지 않았습니다.");
    String clean = code == null ? "" : code.replace("#_", "").trim();
    if (clean.isEmpty()) throw new IllegalArgumentException("인증 코드가 없습니다.");
    InstagramApi.TokenResult shortToken = instagram.exchangeCode(clean);
    InstagramApi.TokenResult longToken = instagram.exchangeLongLived(shortToken.accessToken());
    InstagramApi.Profile me = instagram.me(longToken.accessToken());
    String igUserId = me.userId() != null ? me.userId() : shortToken.userId();
    if (igUserId == null) throw new IllegalStateException("인스타그램 계정 ID를 받지 못했습니다.");
    SnsAccount account = accounts.findAll().stream()
        .filter(a -> igUserId.equals(a.getIgUserId()))
        .findFirst()
        .orElseGet(() -> SnsAccount.instagram(igUserId, me.username()));
    account.setUsername(me.username());
    account.setAccessTokenEnc(cipher.encrypt(longToken.accessToken()));
    LocalDateTime now = SnsTime.now();
    account.setTokenRefreshedAt(now);
    account.setTokenExpiresAt(longToken.expiresInSeconds() > 0 ? now.plusSeconds(longToken.expiresInSeconds()) : now.plusDays(60));
    account.setStatus(SnsAccount.Status.CONNECTED);
    account.setLastError(null);
    return accounts.save(account);
  }

  public SnsAccount disconnect(Long accountId) {
    SnsAccount a = accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    a.setAccessTokenEnc(null);
    a.setTokenExpiresAt(null);
    a.setStatus(SnsAccount.Status.DISCONNECTED);
    return accounts.save(a);
  }

  /** 만료가 가까운 토큰을 갱신한다(매일). 이미 만료됐으면 EXPIRED로 바꾸고 다시 연결하게 한다. */
  public void refreshExpiring() {
    LocalDateTime now = SnsTime.now();
    for (SnsAccount a : accounts.findAll()) {
      if (a.getStatus() != SnsAccount.Status.CONNECTED || a.getTokenExpiresAt() == null) continue;
      if (a.getTokenExpiresAt().isBefore(now)) {
        a.setStatus(SnsAccount.Status.EXPIRED);
        a.setLastError("토큰이 만료되었습니다. 다시 연결해 주세요.");
        accounts.save(a);
        continue;
      }
      if (a.getTokenExpiresAt().isAfter(now.plusDays(REFRESH_BEFORE_DAYS))) continue;
      refreshOne(a);
    }
  }

  public SnsAccount refreshExpiringOne(Long accountId) {
    return refreshOne(accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다.")));
  }

  public SnsAccount refreshOne(SnsAccount a) {
    try {
      InstagramApi.TokenResult r = instagram.refresh(cipher.decrypt(a.getAccessTokenEnc()));
      LocalDateTime now = SnsTime.now();
      a.setAccessTokenEnc(cipher.encrypt(r.accessToken()));
      a.setTokenRefreshedAt(now);
      a.setTokenExpiresAt(r.expiresInSeconds() > 0 ? now.plusSeconds(r.expiresInSeconds()) : now.plusDays(60));
      a.setLastError(null);
    } catch (RuntimeException e) {
      a.setLastError("토큰 갱신 실패: " + e.getMessage());
      log.warn("[sns] 토큰 갱신 실패 account={}", a.getId());
    }
    return accounts.save(a);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
