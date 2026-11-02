package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.dto.SnsDtos;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsAutomationRule;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsBrandPreset;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsGenerationLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsReferenceImage;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsTime;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsAutomationRuleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsBrandPresetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsGenerationLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsReferenceImageRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 화면용 조회·편집. 삭제 정책:
 * 브랜드·참고 이미지·계정은 규칙이 쓰고 있으면 삭제를 막는다(참조 차단). 규칙은 게시물이 있으면 삭제 대신 끄기.
 * 게시물은 지우지 않고 '버림'(DISCARDED)으로 둔다(소프트).
 */
@Service
public class SnsAdminService {

  private final SnsAccountRepository accounts;
  private final SnsBrandPresetRepository brands;
  private final SnsReferenceImageRepository references;
  private final SnsAutomationRuleRepository rules;
  private final SnsPostRepository posts;
  private final SnsGenerationLogRepository logs;
  private final SnsBudgetService budget;
  private final SnsCaptionService captionService;
  private final SnsTokenCipher cipher;
  private final SnsProperties properties;

  public SnsAdminService(SnsAccountRepository accounts, SnsBrandPresetRepository brands, SnsReferenceImageRepository references,
      SnsAutomationRuleRepository rules, SnsPostRepository posts, SnsGenerationLogRepository logs, SnsBudgetService budget,
      SnsCaptionService captionService, SnsTokenCipher cipher, SnsProperties properties) {
    this.accounts = accounts;
    this.brands = brands;
    this.references = references;
    this.rules = rules;
    this.posts = posts;
    this.logs = logs;
    this.budget = budget;
    this.captionService = captionService;
    this.cipher = cipher;
    this.properties = properties;
  }

  // ---- 홈 ----

  public SnsDtos.Summary summary() {
    LocalDateTime now = SnsTime.now();
    LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
    long spent = budget.spentThisMonth();
    List<String> alerts = new ArrayList<>();
    if (!properties.budgetConfigured()) alerts.add("월 상한(SNS_MONTHLY_BUDGET_KRW)이 설정되지 않아 문구·이미지를 만들지 않습니다.");
    else if (spent >= properties.getMonthlyBudgetKrw()) alerts.add(String.format("이번 달 상한 %,d원에 닿아 생성을 멈췄습니다.", properties.getMonthlyBudgetKrw()));
    if (!notBlank(properties.getWorkerToken())) alerts.add("GPU 작업자 토큰(SNS_WORKER_TOKEN)이 없어 이미지를 만들 수 없습니다.");
    if (!cipher.isConfigured()) alerts.add("토큰 암호화 키(SNS_TOKEN_KEY_BASE64)가 없어 인스타그램 계정을 연결할 수 없습니다.");
    if (!properties.metaConfigured()) alerts.add("Meta 앱 정보(SNS_META_APP_ID·SNS_META_APP_SECRET)가 없어 계정을 연결할 수 없습니다.");
    for (SnsAccount a : accounts.findAll()) {
      if (a.getStatus() == SnsAccount.Status.EXPIRED) alerts.add("@" + a.getUsername() + " 토큰이 만료되었습니다. 다시 연결해 주세요.");
      else if (expiringSoon(a)) alerts.add("@" + a.getUsername() + " 토큰이 " + SnsPublishService.WARN_BEFORE_DAYS + "일 안에 만료됩니다.");
      if (a.getLastError() != null && a.getStatus() == SnsAccount.Status.CONNECTED) alerts.add("@" + a.getUsername() + ": " + a.getLastError());
    }
    return new SnsDtos.Summary(
        posts.countByStatus(SnsPost.Status.REVIEW), posts.countByStatus(SnsPost.Status.GENERATING),
        posts.countByStatus(SnsPost.Status.FAILED), posts.countScheduledBetween(dayStart, dayStart.plusDays(1)),
        posts.countPublishedSince(budget.monthStart()), logs.countGenerationsSince(budget.monthStart()), spent,
        properties.getMonthlyBudgetKrw(), properties.budgetConfigured(), properties.isSchedulerEnabled(),
        notBlank(properties.getWorkerToken()), properties.metaConfigured(), cipher.isConfigured(), alerts);
  }

  public List<SnsDtos.LogView> recentLogs(int limit) {
    return logs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200))).stream()
        .map(SnsDtos.LogView::of).toList();
  }

  // ---- 계정 ----

  public List<SnsDtos.AccountView> accounts() {
    return accounts.findAll().stream().map(a -> SnsDtos.AccountView.of(a, expiringSoon(a))).toList();
  }

  private boolean expiringSoon(SnsAccount a) {
    return a.getStatus() == SnsAccount.Status.CONNECTED && a.getTokenExpiresAt() != null
        && a.getTokenExpiresAt().isBefore(SnsTime.now().plusDays(SnsPublishService.WARN_BEFORE_DAYS));
  }

  @Transactional
  public void deleteAccount(Long id) {
    SnsAccount a = accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
    if (rules.countByAccountId(id) > 0) throw new IllegalStateException("이 계정을 쓰는 규칙이 있어 삭제할 수 없습니다. 규칙에서 계정을 바꾼 뒤 삭제해 주세요.");
    accounts.delete(a);
  }

  // ---- 브랜드 ----

  public List<SnsDtos.BrandView> brands() {
    return brands.findAll().stream().map(b -> SnsDtos.BrandView.of(b, rules.countByBrandId(b.getId()))).toList();
  }

  @Transactional
  public SnsDtos.BrandView saveBrand(Long id, SnsDtos.BrandRequest req) {
    SnsBrandPreset b = id == null ? new SnsBrandPreset() : brands.findById(id).orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
    b.setName(required(req.name(), "이름"));
    b.setIndustry(req.industry());
    b.setAudience(req.audience());
    b.setPurpose(req.purpose());
    b.setInstructions(req.instructions());
    b.setFixedText(req.fixedText());
    b.setFixedTextPosition(req.fixedTextPosition() == null ? SnsBrandPreset.FixedTextPosition.BOTTOM
        : SnsBrandPreset.FixedTextPosition.valueOf(req.fixedTextPosition()));
    b.setBannedPhrases(req.bannedPhrases());
    b.setLanguage(req.language() == null || req.language().isBlank() ? "ko" : req.language());
    SnsBrandPreset saved = brands.save(b);
    return SnsDtos.BrandView.of(saved, saved.getId() == null ? 0 : rules.countByBrandId(saved.getId()));
  }

  @Transactional
  public void deleteBrand(Long id) {
    SnsBrandPreset b = brands.findById(id).orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
    if (rules.countByBrandId(id) > 0) throw new IllegalStateException("이 브랜드를 쓰는 규칙이 있어 삭제할 수 없습니다.");
    brands.delete(b);
  }

  /** 브랜드 문구만 시험 생성(이미지 없음). 문구 비용이 있으면 상한에 포함된다. */
  public SnsDtos.CaptionPreview testCaption(Long brandId) {
    SnsBrandPreset b = brands.findById(brandId).orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
    SnsAutomationRule probe = new SnsAutomationRule();
    probe.setTitle("브랜드 문구 시험");
    probe.setBrandId(brandId);
    probe.setImagePrompt("브랜드 분위기에 맞는 사진 한 장");
    probe.setAiHashtags(true);
    SnsBudgetService.Decision d = budget.check(null, properties.getTextCostKrwPerCall());
    if (!d.allowed()) throw new SnsGenerationService.GenerationBlocked(d.reason());
    long t0 = System.currentTimeMillis();
    try {
      SnsCaptionService.Generated g = captionService.generate(probe, b, List.of());
      logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.TEXT, null, null, g.model(), g.prompt(),
          properties.getTextCostKrwPerCall(), System.currentTimeMillis() - t0, true, null));
      return new SnsDtos.CaptionPreview(g.topic(), g.caption(), g.hashtags(), g.imagePromptEn());
    } catch (RuntimeException e) {
      logs.save(SnsGenerationLog.of(SnsGenerationLog.Kind.TEXT, null, null, null, null,
          properties.getTextCostKrwPerCall(), System.currentTimeMillis() - t0, false, e.getMessage()));
      throw e;
    }
  }

  // ---- 참고 이미지 ----

  public List<SnsDtos.ReferenceView> references() {
    return references.findAll().stream().map(r -> SnsDtos.ReferenceView.of(r, rules.countByReferenceImageId(r.getId()))).toList();
  }

  @Transactional
  public SnsDtos.ReferenceView addReference(String label, String brand, String storageKey, boolean approved) {
    SnsReferenceImage r = new SnsReferenceImage();
    r.setLabel(required(label, "이름"));
    r.setBrand(brand);
    r.setFilePath(storageKey);
    r.setContentType("image/jpeg");
    r.setApproved(approved);
    return SnsDtos.ReferenceView.of(references.save(r), 0);
  }

  @Transactional
  public SnsDtos.ReferenceView setReferenceApproved(Long id, boolean approved) {
    SnsReferenceImage r = references.findById(id).orElseThrow(() -> new IllegalArgumentException("참고 이미지를 찾을 수 없습니다."));
    r.setApproved(approved);
    return SnsDtos.ReferenceView.of(references.save(r), rules.countByReferenceImageId(id));
  }

  @Transactional
  public void deleteReference(Long id) {
    SnsReferenceImage r = references.findById(id).orElseThrow(() -> new IllegalArgumentException("참고 이미지를 찾을 수 없습니다."));
    if (rules.countByReferenceImageId(id) > 0) throw new IllegalStateException("이 참고 이미지를 쓰는 규칙이 있어 삭제할 수 없습니다.");
    references.delete(r);
  }

  // ---- 규칙 ----

  public List<SnsDtos.RuleView> rules() {
    Map<Long, String> brandNames = brands.findAll().stream().collect(Collectors.toMap(SnsBrandPreset::getId, SnsBrandPreset::getName));
    Map<Long, SnsAccount> accountById = accounts.findAll().stream().collect(Collectors.toMap(SnsAccount::getId, Function.identity()));
    return rules.findAll().stream().map(r -> SnsDtos.RuleView.of(r,
        r.getAccountId() == null || !accountById.containsKey(r.getAccountId()) ? null : "@" + accountById.get(r.getAccountId()).getUsername(),
        brandNames.get(r.getBrandId()))).toList();
  }

  public SnsAutomationRule rule(Long id) {
    return rules.findById(id).orElseThrow(() -> new IllegalArgumentException("규칙을 찾을 수 없습니다."));
  }

  @Transactional
  public SnsDtos.RuleView saveRule(Long id, SnsDtos.RuleRequest req) {
    SnsAutomationRule r = id == null ? new SnsAutomationRule() : rule(id);
    r.setTitle(required(req.title(), "제목"));
    if (req.brandId() == null || brands.findById(req.brandId()).isEmpty()) throw new IllegalArgumentException("브랜드를 골라 주세요.");
    r.setBrandId(req.brandId());
    if (req.accountId() != null && accounts.findById(req.accountId()).isEmpty()) throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
    r.setAccountId(req.accountId());
    if (req.referenceImageId() != null) {
      SnsReferenceImage ref = references.findById(req.referenceImageId()).orElseThrow(() -> new IllegalArgumentException("참고 이미지를 찾을 수 없습니다."));
      if (!ref.isApproved()) throw new IllegalArgumentException("승인된 참고 이미지만 쓸 수 있습니다.");
    }
    r.setReferenceImageId(req.referenceImageId());
    r.setImagePrompt(required(req.imagePrompt(), "이미지 프롬프트"));
    if (req.imageStyle() != null) r.setImageStyle(SnsAutomationRule.ImageStyle.valueOf(req.imageStyle()));
    String ratio = req.aspectRatio() == null ? "1:1" : req.aspectRatio();
    if (!SnsAutomationRule.ASPECT_RATIOS.contains(ratio)) throw new IllegalArgumentException("비율은 1:1, 4:5, 16:9 중 하나입니다.");
    r.setAspectRatio(ratio);
    r.setImageCount(1); // 1차는 싱글 피드만(카드뉴스는 범위 밖).
    String tags = req.fixedHashtags() == null || req.fixedHashtags().isBlank() ? null : req.fixedHashtags().trim();
    if (tags != null && tags.split("\\s+").length > 5) throw new IllegalArgumentException("고정 해시태그는 최대 5개입니다.");
    r.setFixedHashtags(tags);
    r.setAiHashtags(req.aiHashtags() == null || req.aiHashtags());
    if (req.scheduleType() != null) r.setScheduleType(SnsAutomationRule.ScheduleType.valueOf(req.scheduleType()));
    r.setScheduleDays(req.scheduleDays());
    if (r.getScheduleType() == SnsAutomationRule.ScheduleType.WEEKLY && r.weeklyDays().isEmpty()) {
      throw new IllegalArgumentException("매주 반복은 요일을 하나 이상 골라 주세요.");
    }
    r.setRunTime(req.runTime() == null ? r.getRunTime() : req.runTime());
    r.setStartDate(req.startDate() == null ? (r.getStartDate() == null ? LocalDate.now(SnsTime.ZONE) : r.getStartDate()) : req.startDate());
    if (req.saveMode() != null) {
      SnsAutomationRule.SaveMode mode = SnsAutomationRule.SaveMode.valueOf(req.saveMode());
      if (mode == SnsAutomationRule.SaveMode.PUBLISH) throw new IllegalArgumentException("자동 발행은 아직 켜지 않습니다. 목록 저장(검토 후 발행)을 써 주세요.");
      r.setSaveMode(mode);
    }
    r.setMonthlyBudgetKrw(req.monthlyBudgetKrw() == null || req.monthlyBudgetKrw() <= 0 ? null : req.monthlyBudgetKrw());
    SnsAutomationRule saved = rules.save(r);
    return SnsDtos.RuleView.of(saved, null, brands.findById(saved.getBrandId()).map(SnsBrandPreset::getName).orElse(null));
  }

  @Transactional
  public SnsDtos.RuleView setRuleEnabled(Long id, boolean enabled) {
    SnsAutomationRule r = rule(id);
    r.setEnabled(enabled);
    SnsAutomationRule saved = rules.save(r);
    return SnsDtos.RuleView.of(saved, null, brands.findById(saved.getBrandId()).map(SnsBrandPreset::getName).orElse(null));
  }

  @Transactional
  public void deleteRule(Long id) {
    SnsAutomationRule r = rule(id);
    if (posts.countByRuleId(id) > 0) throw new IllegalStateException("이 규칙으로 만든 게시물이 있어 삭제할 수 없습니다. 대신 꺼 두세요.");
    rules.delete(r);
  }

  // ---- 게시물 ----

  public List<SnsDtos.PostView> posts(String tab, int limit) {
    EnumSet<SnsPost.Status> statuses = switch (tab == null ? "review" : tab) {
      case "scheduled" -> EnumSet.of(SnsPost.Status.SCHEDULED);
      case "published" -> EnumSet.of(SnsPost.Status.PUBLISHED);
      case "failed" -> EnumSet.of(SnsPost.Status.FAILED);
      case "discarded" -> EnumSet.of(SnsPost.Status.DISCARDED);
      default -> EnumSet.of(SnsPost.Status.REVIEW, SnsPost.Status.GENERATING);
    };
    Map<Long, String> titles = rules.findAll().stream().collect(Collectors.toMap(SnsAutomationRule::getId, SnsAutomationRule::getTitle));
    return posts.findByStatusInOrderByCreatedAtDesc(statuses, PageRequest.of(0, Math.min(Math.max(limit, 1), 200))).stream()
        .map(p -> SnsDtos.PostView.of(p, titles.get(p.getRuleId()))).toList();
  }

  public List<SnsDtos.PostView> trialPosts(Long ruleId) {
    String title = rule(ruleId).getTitle();
    return posts.findByRuleIdAndTrialTrueOrderByCreatedAtDesc(ruleId, PageRequest.of(0, 5)).stream()
        .map(p -> SnsDtos.PostView.of(p, title)).toList();
  }

  public SnsDtos.PostView post(Long id) {
    SnsPost p = posts.findById(id).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    return SnsDtos.PostView.of(p, rules.findById(p.getRuleId()).map(SnsAutomationRule::getTitle).orElse(null));
  }

  @Transactional
  public SnsDtos.PostView editPost(Long id, SnsDtos.PostEditRequest req) {
    SnsPost p = posts.findById(id).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (p.getStatus() != SnsPost.Status.REVIEW && p.getStatus() != SnsPost.Status.SCHEDULED && p.getStatus() != SnsPost.Status.FAILED) {
      throw new IllegalStateException("검토 대기·예약·실패 상태에서만 문구를 고칠 수 있습니다.");
    }
    String caption = required(req.caption(), "문구");
    String hashtags = SnsCaptionService.composeHashtags(
        req.hashtags() == null ? List.of() : List.of(req.hashtags().trim().split("\\s+")), List.of());
    if ((caption.strip() + "\n\n" + hashtags).length() > SnsCaptionService.MAX_CAPTION_CHARS) {
      throw new IllegalArgumentException("문구와 해시태그 합이 2,200자를 넘습니다.");
    }
    p.setCaption(caption.strip());
    p.setHashtags(hashtags);
    if (p.getStatus() == SnsPost.Status.FAILED && p.getImageStatus() == SnsPost.PartStatus.DONE && p.getCaptionStatus() == SnsPost.PartStatus.DONE) {
      // 발행 실패 글을 고치면 다시 검토 대기로.
      p.setStatus(SnsPost.Status.REVIEW);
      p.setFailReason(null);
    }
    return SnsDtos.PostView.of(posts.save(p), rules.findById(p.getRuleId()).map(SnsAutomationRule::getTitle).orElse(null));
  }

  @Transactional
  public SnsDtos.PostView discard(Long id) {
    SnsPost p = posts.findById(id).orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다."));
    if (p.getStatus() == SnsPost.Status.PUBLISHED) throw new IllegalStateException("이미 발행된 게시물은 버릴 수 없습니다(인스타그램에서 직접 삭제).");
    p.setStatus(SnsPost.Status.DISCARDED);
    p.setScheduledAt(null);
    return SnsDtos.PostView.of(posts.save(p), rules.findById(p.getRuleId()).map(SnsAutomationRule::getTitle).orElse(null));
  }

  private static String required(String v, String label) {
    if (v == null || v.isBlank()) throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
    return v.trim();
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
