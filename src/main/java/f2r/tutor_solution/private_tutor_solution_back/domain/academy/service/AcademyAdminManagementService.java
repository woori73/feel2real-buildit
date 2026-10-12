package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.VersionInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminAudit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminWorkingCopy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAdminWorkingCopyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.WorkingCopyConflictException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자(ADMIN)의 타인 홈페이지 관리. 모든 메서드는 <b>대상 academyId를 인자로 명시</b>받으며 로그인한 관리자의
 * 학원을 암묵적으로 쓰지 않는다. 수정은 관리자 전용 비공개 작업본({@link AcademyAdminWorkingCopy})에만 쓰고, 공개
 * 홈페이지는 {@link #publish}에서만 바뀐다. 공개 데이터를 직접 쓰는 기존 AI 채팅 적용 경로는 쓰지 않는다.
 *
 * <p>모든 쓰기·진입은 같은 트랜잭션 안에서 감사 기록을 남긴다({@link AcademyAdminAuditService}).
 */
@Service
@RequiredArgsConstructor
public class AcademyAdminManagementService {

  private final AcademyRepository academyRepository;
  private final AcademyAdminWorkingCopyRepository repository;
  private final AcademyWorkingCopyRepository memberWorkingCopies;
  private final AcademyWorkingCopyService support;
  private final AcademyService academyService;
  private final AcademyPageVersionService versionService;
  private final AcademyAdminAuditService audit;
  private final AuthenticatedUserResolver authResolver;

  /** 관리 화면용 학원 요약. */
  public record AcademySummary(
      Long academyId, String name, String handle, String slug, AcademyStatus status, boolean visible,
      Long ownerUserId, String ownerLoginId) {}

  /** 확인 화면용: 현재 공개 내용(변경 비교 기준)과 회원이 게시하지 않은 작업본이 있는지(내용은 노출하지 않는다). */
  public record LiveView(String intro, Map<String, String> builderDraft) {}

  public record AdminView(AcademySummary academy, State state, LiveView live, boolean memberDraftDirty) {}

  /** 공개 변경 반영 결과: 새 상태 + 양쪽이 모두 바꿔 공개 값을 유지한 항목(충돌) + 관리자 값이 유지된 항목. */
  public record RebaseResult(State state, List<String> conflicts, List<String> keptMine) {}

  public record PublishResult(boolean firstPublish, State state) {}

  /** 개별 학원 관리 화면 진입. 작업본이 없으면 현재 공개 내용으로 만든다(공개본 불변). 접근을 감사 기록에 남긴다. */
  @Transactional
  public AdminView open(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId) {
    Academy academy = target(admin, academyId);
    AcademyAdminWorkingCopy wc = loadOrCreate(academy, admin.loginId());
    State state = state(academy, wc);
    audit.record(admin, academy, AcademyAdminAuditService.ACCESS, state.revision(), false,
        "관리 화면 진입", Map.of("status", academy.getStatus().name(), "dirty", state.dirty()));
    return new AdminView(summary(academy), state, liveView(academy), memberDraftDirty(academy));
  }

  /**
   * 공개 변경 반영(rebase): 작업본을 만든 뒤 공개 홈페이지가 바뀌었을 때, 관리자가 바꾼 필드만 새 공개 내용 위에
   * 얹는다. 양쪽이 같은 필드를 다르게 바꾼 경우는 공개 쪽 값을 유지하고 충돌로 알려 어느 쪽 수정도 조용히
   * 덮어쓰지 않는다. 공개본은 바뀌지 않는다. 감사 기록에는 수정(SAVE)으로 남는다.
   */
  @Transactional
  public RebaseResult rebase(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, long expectedRevision) {
    Academy academy = target(admin, academyId);
    AcademyAdminWorkingCopy wc = loadOrCreate(academy, admin.loginId());
    requireRevision(wc, expectedRevision);

    AcademyPageSettings baseSettings = support.parseSettings(wc.getBasePageSettingsJson());
    boolean baseKnown = wc.getBasePageSettingsJson() != null;
    AcademyPageSettings mine = support.parseSettings(wc.getPageSettingsJson());
    AcademyPageSettings live = academy.getPageSettings();
    AdminDraftMerger.Result merged = AdminDraftMerger.merge(
        wc.getBaseIntro(), baseSettings, wc.getIntro(), mine, academy.getIntro(), live, baseKnown);
    AcademyPageSettings next = AcademyWorkingCopyService.withLiveRenderMode(merged.settings(), live);

    Map<String, Object> details = new LinkedHashMap<>(
        AcademyAdminAuditService.describeChange(wc.getIntro(), merged.intro(), mine, next));
    details.put("rebase", true);
    details.put("conflicts", merged.conflicts());
    details.put("keptMine", merged.keptMine());
    wc.replaceContent(merged.intro(), support.toJson(next), admin.loginId());
    wc.rebase(support.fingerprint(academy.getIntro(), live),
        academy.getIntro(), support.toJson(live), admin.loginId());
    flush(wc);
    audit.record(admin, academy, AcademyAdminAuditService.SAVE, nextRevision(wc), false,
        "공개 변경 반영(rebase)" + (merged.conflicts().isEmpty() ? "" : " — 충돌 " + merged.conflicts().size() + "건 공개 값 유지"),
        details);
    return new RebaseResult(state(academy, wc), merged.conflicts(), merged.keptMine());
  }

  /** 관리자 작업본 수정(소개·페이지 설정). 공개본은 바뀌지 않는다. */
  @Transactional
  public State save(
      JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, long expectedRevision,
      String intro, AcademyPageSettingsUpdateRequest patch) {
    Academy academy = target(admin, academyId);
    AcademyAdminWorkingCopy wc = loadOrCreate(academy, admin.loginId());
    requireRevision(wc, expectedRevision);

    AcademyPageSettings before = support.parseSettings(wc.getPageSettingsJson());
    AcademyPageSettings next = patch == null ? before : academyService.mergePageSettingsForWrite(before, patch);
    String nextIntro = intro == null ? wc.getIntro() : academyService.normalizeIntroForWrite(intro);
    next = AcademyWorkingCopyService.withLiveRenderMode(next, academy.getPageSettings());

    Map<String, Object> details =
        AcademyAdminAuditService.describeChange(wc.getIntro(), nextIntro, before, next);
    wc.replaceContent(nextIntro, support.toJson(next), admin.loginId());
    flush(wc);
    audit.record(admin, academy, AcademyAdminAuditService.SAVE, nextRevision(wc), false,
        "작업본 수정", details);
    return state(academy, wc);
  }

  /** 되돌리기: 관리자 작업본을 현재 공개 내용으로 되돌린다(공개본 불변). */
  @Transactional
  public State discard(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, long expectedRevision) {
    Academy academy = target(admin, academyId);
    AcademyAdminWorkingCopy wc = loadOrCreate(academy, admin.loginId());
    requireRevision(wc, expectedRevision);
    wc.replaceContent(academy.getIntro(), support.toJson(academy.getPageSettings()), admin.loginId());
    wc.rebase(support.fingerprint(academy.getIntro(), academy.getPageSettings()),
        academy.getIntro(), support.toJson(academy.getPageSettings()), admin.loginId());
    flush(wc);
    audit.record(admin, academy, AcademyAdminAuditService.DISCARD, nextRevision(wc), false,
        "작업본을 공개 내용으로 되돌림", Map.of());
    return state(academy, wc);
  }

  /** 복원: 대상 학원의 버전 기록을 관리자 작업본에 불러온다(공개본 불변). 다른 학원의 버전은 403. */
  @Transactional
  public State restore(
      JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, long expectedRevision, Long versionId) {
    Academy academy = target(admin, academyId);
    AcademyAdminWorkingCopy wc = loadOrCreate(academy, admin.loginId());
    requireRevision(wc, expectedRevision);
    AcademyPageVersion version = versionService.getForAcademy(academyId, versionId);

    Map<String, String> overlay = support.parseDraftMap(version.getBuilderDraftJson());
    overlay.remove(AcademyWorkingCopyService.RENDER_MODE_KEY);
    AcademyPageSettings before = support.parseSettings(wc.getPageSettingsJson());
    AcademyPageSettings next = overlay.isEmpty()
        ? before
        : academyService.mergePageSettingsForWrite(before, AcademyWorkingCopyService.builderDraftPatch(overlay));
    String nextIntro = version.getIntro() != null
        ? academyService.normalizeIntroForWrite(version.getIntro())
        : wc.getIntro();
    next = AcademyWorkingCopyService.withLiveRenderMode(next, academy.getPageSettings());

    Map<String, Object> details = new LinkedHashMap<>(
        AcademyAdminAuditService.describeChange(wc.getIntro(), nextIntro, before, next));
    details.put("versionId", versionId);
    details.put("versionNo", version.getVersionNo());
    wc.replaceContent(nextIntro, support.toJson(next), admin.loginId());
    flush(wc);
    audit.record(admin, academy, AcademyAdminAuditService.RESTORE, nextRevision(wc), false,
        "버전 " + version.getVersionNo() + " 복원(작업본)", details);
    return state(academy, wc);
  }

  /**
   * 게시: 관리자 작업본을 공개 홈페이지에 반영한다 — 공개 홈페이지가 바뀌는 유일한 경로.
   *
   * <p>잘못된 학원 보호: {@code confirmAcademyId}와 {@code confirmHandle}이 대상 학원과 일치해야 한다. DRAFT 학원의
   * 최초 공개는 {@code confirmFirstPublish}가 있어야 한다. 반영 전 공개 내용은 버전 기록에 남는다.
   */
  @Transactional
  public PublishResult publish(
      JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, long expectedRevision, boolean force,
      Long confirmAcademyId, String confirmHandle, boolean confirmFirstPublish) {
    authResolver.requireAdmin(admin);
    Academy academy = academyRepository.findForUpdateByAcademyId(academyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    if (!academyId.equals(confirmAcademyId)
        || confirmHandle == null || !confirmHandle.trim().equalsIgnoreCase(String.valueOf(academy.getHandle()))) {
      throw new WorkingCopyConflictException(
          "TARGET_MISMATCH", "게시 대상 확인(학원 ID·핸들)이 일치하지 않습니다. 대상 학원을 다시 확인하세요.");
    }
    boolean firstPublish = academy.getStatus() == AcademyStatus.DRAFT;
    if (firstPublish && !confirmFirstPublish) {
      throw new WorkingCopyConflictException(
          "FIRST_PUBLISH_CONFIRM_REQUIRED", "아직 공개된 적 없는 학원입니다. 최초 공개를 확인해야 게시할 수 있습니다.");
    }
    AcademyAdminWorkingCopy wc = repository.findByAcademyId(academyId)
        .orElseThrow(() -> new IllegalArgumentException("관리자 작업본이 없습니다."));
    requireRevision(wc, expectedRevision);
    if (!force && !wc.getBaseFingerprint().equals(support.fingerprint(academy.getIntro(), academy.getPageSettings()))) {
      throw new WorkingCopyConflictException(
          "LIVE_CHANGED", "작업본을 만든 뒤 공개 홈페이지가 다른 곳에서 수정되었습니다. 확인 후 다시 게시하세요.");
    }

    AcademyPageSettings live = academy.getPageSettings();
    AcademyPageSettings toPublish =
        AcademyWorkingCopyService.withLiveRenderMode(support.parseSettings(wc.getPageSettingsJson()), live);
    String introToPublish = wc.getIntro() == null ? academy.getIntro() : wc.getIntro();

    Long snapshotId = versionService.snapshotForAcademy(
        academyId, admin.userId(),
        live != null && live.builderDraft() != null ? live.builderDraft() : Map.of(),
        support.pageSettingsMap(live), academy.getIntro(),
        "관리자 게시 전 자동 저장 (" + admin.loginId() + ")");

    try {
      AuditorContextHolder.set(admin.loginId());
      academy.updatePageSettings(toPublish);
      academy.updateIntro(introToPublish);
      if (firstPublish) {
        academy.publishIfDraft();
      }
    } finally {
      AuditorContextHolder.clear();
    }
    wc.rebase(support.fingerprint(academy.getIntro(), academy.getPageSettings()),
        academy.getIntro(), support.toJson(academy.getPageSettings()), admin.loginId());
    flush(wc);

    Map<String, Object> details = new HashMap<>();
    details.put("firstPublish", firstPublish);
    details.put("force", force);
    details.put("previousVersionId", snapshotId);
    audit.record(admin, academy, AcademyAdminAuditService.PUBLISH, nextRevision(wc), true,
        firstPublish ? "최초 공개 게시" : "작업본 게시", details);
    return new PublishResult(firstPublish, state(academy, wc));
  }

  /** 관리자 작업본 기준 비공개 미리보기. DB를 바꾸지 않는다(작업본이 없으면 공개 내용 그대로). */
  @Transactional(readOnly = true)
  public AcademyResponse preview(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId) {
    Academy academy = target(admin, academyId);
    return repository.findByAcademyId(academyId)
        .map(wc -> academyService.toPreviewResponse(
            academy, wc.getIntro(),
            AcademyWorkingCopyService.withLiveRenderMode(
                support.parseSettings(wc.getPageSettingsJson()), academy.getPageSettings())))
        .orElseGet(() -> academyService.toPreviewResponse(academy, null, null));
  }

  @Transactional(readOnly = true)
  public List<VersionInfo> versions(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId) {
    target(admin, academyId);
    return versionService.listForAcademy(academyId);
  }

  @Transactional(readOnly = true)
  public List<AcademyAdminAudit> auditLog(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId, int limit) {
    target(admin, academyId);
    return audit.recent(academyId, limit);
  }

  // ───────────────────────── 내부 ─────────────────────────

  private Academy target(JwtTokenProvider.AuthenticatedPrincipal admin, Long academyId) {
    authResolver.requireAdmin(admin);
    if (academyId == null) {
      throw new IllegalArgumentException("대상 학원(academyId)을 지정해야 합니다.");
    }
    return academyRepository.findById(academyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
  }

  private LiveView liveView(Academy academy) {
    AcademyPageSettings live = academy.getPageSettings();
    Map<String, String> draft = live == null || live.builderDraft() == null ? Map.of() : live.builderDraft();
    return new LiveView(academy.getIntro(), draft);
  }

  /** 회원 본인의 작업본에 아직 게시하지 않은 변경이 있는가(내용은 읽지 않고 지문 비교만). */
  private boolean memberDraftDirty(Academy academy) {
    return memberWorkingCopies.findByAcademyId(academy.getAcademyId()).map(wc -> {
      AcademyPageSettings settings = AcademyWorkingCopyService.withLiveRenderMode(
          support.parseSettings(wc.getPageSettingsJson()), academy.getPageSettings());
      return !support.fingerprint(wc.getIntro(), settings)
          .equals(support.fingerprint(academy.getIntro(), academy.getPageSettings()));
    }).orElse(false);
  }

  private AcademySummary summary(Academy a) {
    return new AcademySummary(
        a.getAcademyId(), a.getName(), a.getHandle(), a.getSlug(), a.getStatus(), a.isVisible(),
        a.getTutor() == null ? null : a.getTutor().getUserId(),
        a.getTutor() == null ? null : a.getTutor().getLoginId());
  }

  private AcademyAdminWorkingCopy loadOrCreate(Academy academy, String actor) {
    return repository.findByAcademyId(academy.getAcademyId()).orElseGet(() -> {
      AcademyAdminWorkingCopy created = new AcademyAdminWorkingCopy(
          academy.getAcademyId(), academy.getIntro(), support.toJson(academy.getPageSettings()),
          support.fingerprint(academy.getIntro(), academy.getPageSettings()), actor);
      try {
        return repository.saveAndFlush(created);
      } catch (DataIntegrityViolationException race) {
        return repository.findByAcademyId(academy.getAcademyId()).orElseThrow(() -> race);
      }
    });
  }

  private void requireRevision(AcademyAdminWorkingCopy wc, long expected) {
    if (wc.getRevision() == null || wc.getRevision() != expected) {
      throw new WorkingCopyConflictException(
          "REVISION_CONFLICT", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
    }
  }

  private void flush(AcademyAdminWorkingCopy wc) {
    try {
      repository.saveAndFlush(wc);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new WorkingCopyConflictException(
          "REVISION_CONFLICT", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
    }
  }

  private Long nextRevision(AcademyAdminWorkingCopy wc) {
    return wc.getRevision();
  }

  private State state(Academy academy, AcademyAdminWorkingCopy wc) {
    AcademyPageSettings settings = AcademyWorkingCopyService.withLiveRenderMode(
        support.parseSettings(wc.getPageSettingsJson()), academy.getPageSettings());
    String liveFp = support.fingerprint(academy.getIntro(), academy.getPageSettings());
    String wcFp = support.fingerprint(wc.getIntro(), settings);
    return new State(
        wc.getRevision(), wc.getIntro(), settings,
        !wcFp.equals(liveFp), !wc.getBaseFingerprint().equals(liveFp));
  }
}
