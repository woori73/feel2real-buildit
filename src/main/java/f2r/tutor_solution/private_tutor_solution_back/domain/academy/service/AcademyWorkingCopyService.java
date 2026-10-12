package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyWorkingCopy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 통합 화면의 작업본 서비스.
 *
 * <p>규칙(이 서비스가 지키는 불변식):
 * <ul>
 *   <li>작업본 저장·되돌리기·버리기는 공개 홈페이지(academies)를 건드리지 않는다.</li>
 *   <li>공개본은 {@link #publish}에서만 바뀐다. 그 직전에 기존 버전 기록에 현재 공개 내용을 남긴다.</li>
 *   <li>{@code homepage.renderMode}(시각 편집기 게시 상태)는 작업본이 바꾸지 못한다 — 항상 공개본 값을 따른다.</li>
 *   <li>작업본 저장은 revision(낙관적 잠금)으로, 게시는 공개 내용 지문으로 충돌을 막는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcademyWorkingCopyService {

  static final String RENDER_MODE_KEY = "homepage.renderMode";

  private final AcademyWorkingCopyRepository repository;
  private final AcademyRepository academyRepository;
  private final AcademyService academyService;
  private final AcademyPageVersionService versionService;
  private final AuthenticatedUserResolver authResolver;
  private final ObjectMapper objectMapper;

  /** 충돌 예외. code: REVISION_CONFLICT(작업본이 그 사이 바뀜) 또는 LIVE_CHANGED(공개 내용이 그 사이 바뀜). */
  public static class WorkingCopyConflictException extends RuntimeException {
    private final String code;

    public WorkingCopyConflictException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  /**
   * 작업본 상태.
   *
   * @param dirty 작업본이 공개본과 다른가(게시할 변경이 있는가)
   * @param liveChanged 작업본을 만든 뒤 공개 내용이 따로 바뀌었는가(게시 시 충돌 가능)
   */
  public record State(
      long revision, String intro, AcademyPageSettings pageSettings, boolean dirty, boolean liveChanged) {}

  /** 작업본을 연다. 없으면 현재 공개 내용으로 복제해 만든다(공개본은 변경하지 않는다). */
  @Transactional
  public State open(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = ownedAcademy(principal);
    return state(academy, loadOrCreate(academy, principal.loginId()));
  }

  /**
   * 작업본을 수정한다. 소개(intro)와 page settings 부분 변경을 함께 받을 수 있고, 검증은 공개 저장과 같다.
   *
   * @param expectedRevision 클라이언트가 마지막으로 본 revision
   */
  @Transactional
  public State save(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      long expectedRevision,
      String intro,
      AcademyPageSettingsUpdateRequest patch) {
    Academy academy = ownedAcademy(principal);
    AcademyWorkingCopy wc = loadOrCreate(academy, principal.loginId());
    requireRevision(wc, expectedRevision);

    AcademyPageSettings current = parseSettings(wc.getPageSettingsJson());
    AcademyPageSettings next = patch == null
        ? current
        : academyService.mergePageSettingsForWrite(current, patch);
    String nextIntro = intro == null ? wc.getIntro() : academyService.normalizeIntroForWrite(intro);
    next = withLiveRenderMode(next, academy.getPageSettings());

    wc.replaceContent(nextIntro, toJson(next), principal.loginId());
    flush(wc);
    return state(academy, wc);
  }

  /** 작업본을 현재 공개 내용으로 되돌린다(공개본은 변경하지 않는다). */
  @Transactional
  public State discard(JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision) {
    Academy academy = ownedAcademy(principal);
    AcademyWorkingCopy wc = loadOrCreate(academy, principal.loginId());
    requireRevision(wc, expectedRevision);
    wc.replaceContent(academy.getIntro(), toJson(academy.getPageSettings()), principal.loginId());
    wc.rebase(fingerprint(academy.getIntro(), academy.getPageSettings()), principal.loginId());
    flush(wc);
    return state(academy, wc);
  }

  /**
   * 기존 버전 기록의 내용을 작업본에 불러온다(공개본은 변경하지 않는다).
   * 버전의 builderDraft 키를 작업본에 덮어쓰고, 버전에 intro가 있으면 intro도 되돌린다.
   */
  @Transactional
  public State restoreFromVersion(
      JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision, Long versionId) {
    Academy academy = ownedAcademy(principal);
    AcademyWorkingCopy wc = loadOrCreate(academy, principal.loginId());
    requireRevision(wc, expectedRevision);
    AcademyPageVersion version = versionService.getForRestore(principal, versionId);

    Map<String, String> overlay = parseDraftMap(version.getBuilderDraftJson());
    overlay.remove(RENDER_MODE_KEY);
    AcademyPageSettings current = parseSettings(wc.getPageSettingsJson());
    AcademyPageSettings next = overlay.isEmpty()
        ? current
        : academyService.mergePageSettingsForWrite(current, builderDraftPatch(overlay));
    String nextIntro = version.getIntro() != null
        ? academyService.normalizeIntroForWrite(version.getIntro())
        : wc.getIntro();
    next = withLiveRenderMode(next, academy.getPageSettings());

    wc.replaceContent(nextIntro, toJson(next), principal.loginId());
    flush(wc);
    return state(academy, wc);
  }

  /**
   * 작업본을 공개본에 반영한다 — 공개 홈페이지가 바뀌는 유일한 경로.
   *
   * <p>학원 행을 잠가 같은 학원의 동시 게시를 직렬화하고, 공개 내용이 작업본 기준 이후 따로 바뀌었으면
   * {@code force}가 아닌 한 거부한다. 반영 전 공개 내용은 기존 버전 기록에 남아 복원할 수 있다.
   */
  @Transactional
  public State publish(
      JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision, boolean force) {
    Academy academy = academyService.lockMyAcademyForUpdate(principal);
    AcademyWorkingCopy wc = repository.findByAcademyId(academy.getAcademyId())
        .orElseThrow(() -> new IllegalArgumentException("작업본이 없습니다."));
    requireRevision(wc, expectedRevision);

    if (!force && !wc.getBaseFingerprint().equals(fingerprint(academy.getIntro(), academy.getPageSettings()))) {
      throw new WorkingCopyConflictException(
          "LIVE_CHANGED", "작업본을 만든 뒤 공개 홈페이지가 다른 곳에서 수정되었습니다. 확인 후 다시 게시하세요.");
    }

    AcademyPageSettings live = academy.getPageSettings();
    AcademyPageSettings toPublish = withLiveRenderMode(parseSettings(wc.getPageSettingsJson()), live);
    String introToPublish = wc.getIntro() == null ? academy.getIntro() : wc.getIntro();

    // 게시 전 공개 내용을 버전 기록에 남긴다(되돌리기 기준점).
    versionService.snapshot(
        principal,
        live != null && live.builderDraft() != null ? live.builderDraft() : Map.of(),
        pageSettingsMap(live),
        academy.getIntro(),
        "게시 전 자동 저장");

    try {
      AuditorContextHolder.set(principal.loginId());
      academy.updatePageSettings(toPublish);
      academy.updateIntro(introToPublish);
    } finally {
      AuditorContextHolder.clear();
    }
    wc.rebase(fingerprint(academy.getIntro(), academy.getPageSettings()), principal.loginId());
    flush(wc);
    return state(academy, wc);
  }

  /** 작업본 기준 비공개 미리보기 응답. 작업본이 없으면 현재 공개 내용 그대로다. DB는 바꾸지 않는다. */
  @Transactional(readOnly = true)
  public AcademyResponse preview(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = ownedAcademy(principal);
    return repository.findByAcademyId(academy.getAcademyId())
        .map(wc -> academyService.toPreviewResponse(
            academy, wc.getIntro(),
            withLiveRenderMode(parseSettings(wc.getPageSettingsJson()), academy.getPageSettings())))
        .orElseGet(() -> academyService.toPreviewResponse(academy, null, null));
  }

  // ───────────────────────── 내부 ─────────────────────────

  private Academy ownedAcademy(JwtTokenProvider.AuthenticatedPrincipal principal) {
    authResolver.requireTutor(principal);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy를 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(academy);
    return academy;
  }

  private AcademyWorkingCopy loadOrCreate(Academy academy, String actor) {
    return repository.findByAcademyId(academy.getAcademyId()).orElseGet(() -> {
      AcademyWorkingCopy created = new AcademyWorkingCopy(
          academy.getAcademyId(),
          academy.getIntro(),
          toJson(academy.getPageSettings()),
          fingerprint(academy.getIntro(), academy.getPageSettings()),
          actor);
      try {
        return repository.saveAndFlush(created);
      } catch (DataIntegrityViolationException race) {
        // 동시에 다른 요청이 먼저 만들었다 → 그 행을 쓴다.
        return repository.findByAcademyId(academy.getAcademyId()).orElseThrow(() -> race);
      }
    });
  }

  private void requireRevision(AcademyWorkingCopy wc, long expected) {
    if (wc.getRevision() == null || wc.getRevision() != expected) {
      throw new WorkingCopyConflictException(
          "REVISION_CONFLICT", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
    }
  }

  private void flush(AcademyWorkingCopy wc) {
    try {
      repository.saveAndFlush(wc);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new WorkingCopyConflictException(
          "REVISION_CONFLICT", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
    }
  }

  private State state(Academy academy, AcademyWorkingCopy wc) {
    AcademyPageSettings settings =
        withLiveRenderMode(parseSettings(wc.getPageSettingsJson()), academy.getPageSettings());
    String liveFp = fingerprint(academy.getIntro(), academy.getPageSettings());
    String wcFp = fingerprint(wc.getIntro(), settings);
    return new State(
        wc.getRevision(), wc.getIntro(), settings,
        !wcFp.equals(liveFp), !wc.getBaseFingerprint().equals(liveFp));
  }

  /** 작업본은 시각 편집기 게시 상태(renderMode)를 바꿀 수 없다 — 항상 공개본 값을 따른다. */
  static AcademyPageSettings withLiveRenderMode(AcademyPageSettings work, AcademyPageSettings live) {
    if (work == null) {
      return null;
    }
    Map<String, String> draft = new HashMap<>();
    if (work.builderDraft() != null) {
      draft.putAll(work.builderDraft());
    }
    draft.remove(RENDER_MODE_KEY);
    if (live != null && live.builderDraft() != null && live.builderDraft().get(RENDER_MODE_KEY) != null) {
      draft.put(RENDER_MODE_KEY, live.builderDraft().get(RENDER_MODE_KEY));
    }
    return new AcademyPageSettings(
        work.blogUrl(), work.storeUrl(), work.naverMapUrl(), work.heroYoutubeUrl(),
        work.sectionVisibility(), work.sectionOrder(), draft.isEmpty() ? null : draft,
        work.businessInfo());
  }

  private static AcademyPageSettingsUpdateRequest builderDraftPatch(Map<String, String> draft) {
    return new AcademyPageSettingsUpdateRequest(null, null, null, null, null, null, null, draft);
  }

  private Map<String, Object> pageSettingsMap(AcademyPageSettings settings) {
    if (settings == null) {
      return Map.of();
    }
    return objectMapper.convertValue(settings, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, String> parseDraftMap(String json) {
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      Map<String, String> parsed = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("버전 내용을 읽을 수 없습니다.", e);
    }
  }

  private AcademyPageSettings parseSettings(String json) {
    if (json == null || json.isBlank() || "null".equals(json)) {
      return null;
    }
    try {
      return objectMapper.readValue(json, AcademyPageSettings.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("작업본 설정을 읽을 수 없습니다.", e);
    }
  }

  /** 키 순서를 고정한 JSON — 저장값과 지문이 로드 순서에 흔들리지 않게 한다. */
  private String toJson(Object value) {
    try {
      return canonicalMapper().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("JSON 직렬화 실패", e);
    }
  }

  private ObjectMapper canonicalMapper() {
    return objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  /** 소개 + page settings 내용의 SHA-256 지문. */
  String fingerprint(String intro, AcademyPageSettings settings) {
    String material = (intro == null ? "" : intro) + "\n" + toJson(settings);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
    }
  }
}
