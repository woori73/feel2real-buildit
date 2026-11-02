package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsBrandPresetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물 생성 흐름: 규칙 실행 → 문구(LLM, 서버에서 바로) → 이미지(GPU 작업자가 가져가 처리) → 둘 다 끝나면 검토 대기(REVIEW).
 * 문구·이미지 모두 실패 시 1회만 다시 시도하고, 그다음은 실패(FAILED)에 사유를 남긴다(무한 재시도 금지).
 */
@Service
public class SnsGenerationService {

  private static final Logger log = LoggerFactory.getLogger(SnsGenerationService.class);
  public static final int MAX_ATTEMPTS = 2;
  /** GPU 작업자가 가져간 뒤 이 시간 안에 결과가 없으면 멈춘 것으로 보고 다시 내준다. */
  public static final int IMAGE_LEASE_MINUTES = 15;

  public static class GenerationBlocked extends RuntimeException {
    public GenerationBlocked(String message) {
      super(message);
    }
  }

  private final SnsAutomationRuleRepository rules;
  private final SnsBrandPresetRepository brands;
  private final SnsPostRepository posts;
  private final SnsGenerationLogRepository logs;
  private final SnsCaptionService captionService;
  private final SnsBudgetService budget;
  private final SnsProperties properties;
  private final Executor executor;
  private final ExecutorService ownedExecutor;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  public SnsGenerationService(SnsAutomationRuleRepository rules, SnsBrandPresetRepository brands, SnsPostRepository posts,
      SnsGenerationLogRepository logs, SnsCaptionService captionService, SnsBudgetService budget, SnsProperties properties) {
    this(rules, brands, posts, logs, captionService, budget, properties, null);
  }

  /** 테스트용: 문구 생성을 같은 스레드에서 돌리려면 Runnable::run을 넘긴다. */
  SnsGenerationService(SnsAutomationRuleRepository rules, SnsBrandPresetRepository brands, SnsPostRepository posts,
      SnsGenerationLogRepository logs, SnsCaptionService captionService, SnsBudgetService budget, SnsProperties properties,
      Executor executor) {
    this.rules = rules;
    this.brands = brands;
    this.posts = posts;
    this.logs = logs;
    this.captionService = captionService;
    this.budget = budget;
    this.properties = properties;
    if (executor == null) {
      this.ownedExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sns-caption");
        t.setDaemon(true);
        return t;
      });
      this.executor = ownedExecutor;
    } else {
      this.ownedExecutor = null;
      this.executor = executor;
    }
  }

  @PreDestroy
  void shutdown() {
    if (ownedExecutor != null) ownedExecutor.shutdownNow();
  }

  /** 규칙을 한 번 실행한다(예약·즉시 실행·시험 생성 공통). 상한에 막히면 GenerationBlocked. */
  public SnsPost startRun(SnsAutomationRule rule, boolean trial) {
    SnsBudgetService.Decision d = budget.check(rule, budget.estimateRunCost(rule));
    if (!d.allowed()) throw new GenerationBlocked(d.reason());
    if (brands.findById(rule.getBrandId()).isEmpty()) throw new GenerationBlocked("규칙의 브랜드 프리셋을 찾을 수 없습니다.");
    SnsPost post = new SnsPost();
    post.setRuleId(rule.getId());
    post.setTrial(trial);
    post.setStatus(SnsPost.Status.GENERATING);
    post.setCaptionStatus(SnsPost.PartStatus.PENDING);
    post.setImageStatus(SnsPost.PartStatus.PENDING);
    SnsPost saved = posts.save(post);
    Long postId = saved.getId();
    executor.execute(() -> {
      try {
        generateCaption(postId);
      } catch (RuntimeException e) {
        log.warn("[sns] 문구 생성 처리 오류 post={}: {}", postId, e.getClass().getSimpleName());
      }
    });
    return saved;
  }

  /** 문구 생성(최대 2회). 성공하면 이미지 작업을 GPU 작업자에게 연다. */
  public void generateCaption(Long postId) {
    SnsPost post = posts.findById(postId).orElse(null);
    if (post == null || post.getStatus() != SnsPost.Status.GENERATING) return;
    SnsAutomationRule rule = rules.findById(post.getRuleId()).orElse(null);
    SnsBrandPreset brand = rule == null ? null : brands.findById(rule.getBrandId()).orElse(null);
    if (rule == null || brand == null) {
      post.fail("규칙 또는 브랜드 프리셋이 삭제되었습니다.");
      posts.save(post);
      return;
    }
    List<String> recent = posts.recentTopics(rule.getId(), PageRequest.of(0, 30));
    post.setCaptionStatus(SnsPost.PartStatus.RUNNING);
    posts.save(post);
    String lastError = null;
    while (post.getCaptionAttempts() < MAX_ATTEMPTS) {
      post.setCaptionAttempts(post.getCaptionAttempts() + 1);
      long t0 = System.currentTimeMillis();
      try {
        SnsCaptionService.Generated g = captionService.generate(rule, brand, recent);
        logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.TEXT, rule.getId(), postId, g.model(), g.prompt(),
            properties.getTextCostKrwPerCall(), System.currentTimeMillis() - t0, true, null));
        post.setTopic(g.topic());
        post.setCaption(g.caption());
        post.setHashtags(g.hashtags());
        post.setImagePrompt(composeImagePrompt(rule, g.imagePromptEn()));
        post.setCaptionStatus(SnsPost.PartStatus.DONE);
        posts.save(post);
        return;
      } catch (RuntimeException e) {
        lastError = e instanceof SnsCaptionService.CaptionRejected ? e.getMessage() : "문구 생성 중 오류";
        logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.TEXT, rule.getId(), postId, null, null,
            properties.getTextCostKrwPerCall(), System.currentTimeMillis() - t0, false, lastError));
        posts.save(post);
      }
    }
    post.setCaptionStatus(SnsPost.PartStatus.FAILED);
    post.fail("문구 생성 실패(2회): " + lastError);
    posts.save(post);
  }

  /** 이미지 모델에 넘길 프롬프트: LLM이 만든 영어 묘사 + 스타일. 한국어 원문 지시는 규칙에 남아 있다. */
  static String composeImagePrompt(SnsAutomationRule rule, String english) {
    String style = switch (rule.getImageStyle()) {
      case PHOTO -> "photorealistic, professional photography, natural skin texture, soft cinematic lighting";
      case ILLUSTRATION -> "clean digital illustration";
      case THREE_D -> "high quality 3D render";
    };
    return english.strip() + ", " + style + ", no text, no letters, no logo, no watermark";
  }

  // ---- GPU 작업자 쪽 ----

  public record ImageJob(Long postId, Long ruleId, String prompt, String aspectRatio, Long referenceImageId, int attempt) {}

  /** 다음 이미지 작업 하나를 원자적으로 가져간다. 상한에 닿았으면 아무것도 내주지 않는다. */
  @Transactional
  public Optional<ImageJob> claimNextImageJob() {
    LocalDateTime now = SnsTime.now();
    LocalDateTime stale = now.minusMinutes(IMAGE_LEASE_MINUTES);
    for (SnsPost candidate : posts.imageJobCandidates(stale, PageRequest.of(0, 5))) {
      SnsAutomationRule rule = rules.findById(candidate.getRuleId()).orElse(null);
      if (rule == null) continue;
      if (candidate.getImageAttempts() >= MAX_ATTEMPTS) {
        // 가져간 뒤 멈춘 작업이 두 번째였다면 더 내주지 않고 실패 처리.
        candidate.setImageStatus(SnsPost.PartStatus.FAILED);
        candidate.fail("이미지 생성 실패(2회): GPU 작업자가 결과를 보내지 않았습니다.");
        posts.save(candidate);
        continue;
      }
      SnsBudgetService.Decision d = budget.check(rule, budget.imageCostFor(properties.getGpuEstimatedSecondsPerImage()));
      if (!d.allowed()) return Optional.empty();
      if (posts.claimImageJob(candidate.getId(), candidate.getImageAttempts(), now, stale) == 1) {
        return Optional.of(new ImageJob(candidate.getId(), rule.getId(), candidate.getImagePrompt(), rule.getAspectRatio(),
            rule.getReferenceImageId(), candidate.getImageAttempts() + 1));
      }
    }
    return Optional.empty();
  }

  /** 진행 중 작업의 참고 이미지(승인된 것만). */
  public Optional<Long> referenceImageIdFor(SnsPost post) {
    return rules.findById(post.getRuleId()).map(SnsAutomationRule::getReferenceImageId);
  }

  /** 작업자가 보낸 이미지 저장 키를 기록하고, 문구도 끝났으면 검토 대기로 넘긴다. */
  @Transactional
  public SnsPost completeImage(Long postId, String storageKey, long gpuMillis, String model) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getImageStatus() != SnsPost.PartStatus.RUNNING || post.getStatus() != SnsPost.Status.GENERATING) {
      throw new IllegalStateException("이미 처리됐거나 진행 중이 아닌 작업입니다.");
    }
    int cost = budget.imageCostFor(Math.max(1, (gpuMillis + 999) / 1000));
    logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.IMAGE, post.getRuleId(), postId, model, post.getImagePrompt(),
        cost, gpuMillis, true, null));
    post.setImagePaths(storageKey);
    post.setImageStatus(SnsPost.PartStatus.DONE);
    if (post.getMediaToken() == null) post.setMediaToken(newToken());
    if (post.getCaptionStatus() == SnsPost.PartStatus.DONE) {
      post.setStatus(SnsPost.Status.REVIEW);
      post.setFailReason(null);
    }
    return posts.save(post);
  }

  /** 작업자가 실패를 알림. 첫 실패면 다시 대기열로, 두 번째면 실패 처리. */
  @Transactional
  public SnsPost failImage(Long postId, String reason, long gpuMillis) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getImageStatus() != SnsPost.PartStatus.RUNNING) throw new IllegalStateException("진행 중인 작업이 아닙니다.");
    String r = reason == null || reason.isBlank() ? "알 수 없는 오류" : reason;
    int cost = gpuMillis > 0 ? budget.imageCostFor((gpuMillis + 999) / 1000) : 0;
    logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.IMAGE, post.getRuleId(), postId, null, post.getImagePrompt(),
        cost, gpuMillis, false, r));
    if (post.getImageAttempts() < MAX_ATTEMPTS) {
      post.setImageStatus(SnsPost.PartStatus.PENDING);
      post.setImageClaimedAt(null);
    } else {
      post.setImageStatus(SnsPost.PartStatus.FAILED);
      post.fail("이미지 생성 실패(2회): " + r);
    }
    return posts.save(post);
  }

  /** 검토 대기·실패 게시물의 이미지를 다시 만든다(문구는 그대로). */
  @Transactional
  public SnsPost regenerateImage(Long postId) {
    SnsPost post = posts.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (post.getStatus() != SnsPost.Status.REVIEW && post.getStatus() != SnsPost.Status.FAILED) {
      throw new IllegalStateException("검토 대기 또는 실패 상태에서만 이미지를 다시 만들 수 있습니다.");
    }
    if (post.getCaptionStatus() != SnsPost.PartStatus.DONE || post.getImagePrompt() == null) {
      throw new IllegalStateException("문구가 없는 게시물은 이미지를 다시 만들 수 없습니다. 규칙을 다시 실행해 주세요.");
    }
    SnsAutomationRule rule = rules.findById(post.getRuleId()).orElseThrow(() -> new IllegalStateException("규칙이 삭제되었습니다."));
    SnsBudgetService.Decision d = budget.check(rule, budget.imageCostFor(properties.getGpuEstimatedSecondsPerImage()));
    if (!d.allowed()) throw new GenerationBlocked(d.reason());
    post.setStatus(SnsPost.Status.GENERATING);
    post.setImageStatus(SnsPost.PartStatus.PENDING);
    post.setImageAttempts(0);
    post.setImageClaimedAt(null);
    post.setFailReason(null);
    return posts.save(post);
  }

  private String newToken() {
    byte[] b = new byte[24];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }
}
