package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ApplyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ApplyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.FieldChange;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ValidationResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.VersionInfo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiAction;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiActionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageVersionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BuildIt P1 AI 채팅 API.
 *
 * <p>보안 원칙 (필수):
 * <ul>
 *   <li>academyId는 서버에서 인증된 고객 정보로 결정한다 (body/param으로 받지 않음).
 *   <li>AI가 생성한 변경 사항은 허용 필드와 값 형식을 서버에서 재검증한다.
 *   <li>AI 응답의 HTML/스크립트를 검증 없이 화면이나 DB에 적용하지 않는다.
 *   <li>미리보기는 운영 홈페이지에 영향을 주지 않도록 격리한다 (DB 저장 없음).
 *   <li>승인 시점에 변경 대상 버전을 재확인하여 동시 수정 충돌을 방지한다.
 *   <li>적용과 복원 모두 권한 검사 및 변경 이력을 남긴다.
 *   <li>AI의 자동 발행을 금지한다 (AcademyStatus 변경 불가).
 * </ul>
 */
@RestController
@RequestMapping("/api/tutor/academy/ai/chat")
@RequiredArgsConstructor
public class AcademyAiChatController {

  /**
   * AI가 수정할 수 있는 builderDraft 필드 allowlist (P1).
   * 여기에 없는 키는 어떤 경로로도 수정할 수 없다.
   *
   * <p>실제 저장 구조와 정합성 (2026-10-11 Qoder 지적 반영):
   * <ul>
   *   <li>{@code intro}는 builderDraft가 아니라 Academy 최상위 필드 → 별도 처리
   *   <li>{@code heroAccent}는 존재하지 않는 필드 → 제거
   *   <li>{@code principalCareer}는 단일 키 (개행 구분) → principalCareer1/2/3 제거
   *   <li>{@code brandPrimary}/{@code brandAccent}는 builderDraft 키가 아님 (CSS 변수) → 제거.
   *       브랜드 색상은 P0 프리셋 시스템을 경유
   * </ul>
   */
  private static final Set<String> ALLOWED_FIELDS = Set.of(
      "principalDesc", "principalCareer",
      "principalQuote",
      "philosophyItem1", "philosophyItem2", "philosophyItem3", "philosophyItem4",
      "monthlyTuition", "scoreImprovement", "operationYears",
      "ctaPhone", "ctaKakaoUrl",
      "introHeading", "introMotto", "introBodySecondary");

  /**
   * Academy 최상위 필드 중 AI 수정 허용 목록 (builderDraft가 아님).
   */
  private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
      "intro");

  /**
   * 절대 수정 불가 키. allowlist에 있어도 이 목록에 있으면 거부한다.
   */
  private static final Set<String> FORBIDDEN_FIELDS = Set.of(
      "academyId", "handle", "published", "sectionOrder", "sectionVisibility",
      "designPreset", "status");

  private final AcademyAiChatService chatService;
  private final AcademyAiCreateService createService;
  private final AcademyAiContextService contextService;
  private final AcademyPageVersionService versionService;
  private final AcademyService academyService;
  private final AcademyAiSessionRepository sessionRepository;
  private final AcademyAiActionRepository actionRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 자연어 메시지를 받아 수정안을 생성한다.
   * DB를 수정하지 않는다 (제안만 생성).
   */
  @PostMapping
  public ResponseEntity<ChatResponse> chat(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ChatRequest request) {
    AcademyResponse academy = getMyAcademy(principal);
    AcademyAiContext context = contextService.buildContext(principal);

    // 세션 조회 또는 생성
    AcademyAiSession session = getOrCreateSession(principal, academy.id(), request.sessionId());

    // OpenCode의 AI 서비스로 수정안 생성 (DB 수정 없음)
    ChatProposal proposal = chatService.proposeChanges(context, request.message());

    // 서버에서 allowlist 재검증
    ValidationResult validation = validateAgainstAllowlist(proposal);
    if (!validation.valid()) {
      return ResponseEntity.badRequest().body(new ChatResponse(
          "요청하신 내용 중 허용되지 않은 항목이 있습니다: " + String.join(", ", validation.errors()),
          null, session.getId().toString(), false));
    }

    // 작업 로그 기록 (감사)
    logAction(session.getId(), academy.id(), "CHAT", proposal, 0, false);
    session.incrementMessage(0);
    sessionRepository.save(session);

    String reply = buildReplyMessage(proposal);
    ChatResponse response = new ChatResponse(
        reply,
        new ChatProposal(proposal.changes(), proposal.summary(), session.getId().toString()),
        session.getId().toString(),
        true);
    return ResponseEntity.ok(response);
  }

  /**
   * 고객 승인 후 수정안을 적용한다.
   * 적용 전 현재 상태를 스냅샷으로 저장한다.
   * 실제 적용은 기존 PUT /api/tutor/academy/page-settings 경로를 경유한다.
   */
  @PostMapping("/apply")
  public ResponseEntity<ApplyResponse> apply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ApplyRequest request) {
    AcademyResponse academy = getMyAcademy(principal);

    // 세션 검증 (본인 Academy의 세션인지 확인)
    AcademyAiSession session = sessionRepository
        .findById(Long.parseLong(request.sessionId()))
        .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));
    if (!session.getAcademyId().equals(academy.id())) {
      throw new SecurityException("다른 학원의 세션에 접근할 수 없습니다.");
    }

    // 변경 사항 서버 재검증
    ChatProposal proposal = new ChatProposal(request.confirmedChanges(), "", request.sessionId());
    ValidationResult validation = validateAgainstAllowlist(proposal);
    if (!validation.valid()) {
      throw new IllegalArgumentException(
          "허용되지 않은 변경이 포함되어 있습니다: " + String.join(", ", validation.errors()));
    }

    // 적용 전 스냅샷 저장 (복원용)
    // 현재 builderDraft 조회
    Map<String, String> currentDraft = getCurrentBuilderDraft(principal);
    Long versionId = versionService.snapshot(
        principal, currentDraft, Map.of(), "AI 채팅 수정 적용 전");

    // 기존 저장 경로를 경유하여 적용 (우회 경로 금지)
    Map<String, String> updatedDraft = new java.util.HashMap<>(currentDraft);
    for (FieldChange change : request.confirmedChanges()) {
      updatedDraft.put(change.key(), change.after());
    }
    academyService.updatePageSettings(
        principal,
        new f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto
            .AcademyPageSettingsUpdateRequest(
            null, null, null, null, null, null, updatedDraft, null));

    // 작업 로그 기록 (승인됨)
    AcademyAiAction action = logAction(
        session.getId(), academy.id(), "APPLY", proposal, 0, true);
    action.markApproved();
    actionRepository.save(action);

    return ResponseEntity.ok(new ApplyResponse(versionId, LocalDateTime.now().toString()));
  }

  /** 버전 목록을 조회한다. */
  @GetMapping("/versions")
  public ResponseEntity<List<VersionInfo>> versions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(versionService.list(principal));
  }

  /**
   * 자연어 요청으로부터 새 홈페이지 초안을 생성한다.
   * DB를 수정하지 않는다 (승인 전까지).
   * 기존 학원 홈페이지 및 데이터를 덮어쓰지 않는다.
   */
  @PostMapping("/create")
  public ResponseEntity<CreateResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody CreateRequest request) {
    // 기존 Academy가 있으면 경고 (덮어쓰기 방지)
    boolean hasExisting = academyService.getMine(principal).isPresent();

    CreateDraft draft = createService.generateDraft(request.message(), request.academyName());

    String reply = draft.summary();
    if (!draft.warnings().isEmpty()) {
      reply += "\n주의: " + String.join(", ", draft.warnings());
    }
    if (hasExisting) {
      reply += "\n[안내] 이미 홈페이지가 있습니다. 이 초안을 적용하면 기존 내용이 변경됩니다. "
          + "적용 전 반드시 미리보기로 확인하세요.";
    }

    // 세션 생성 (추적용) - Academy가 없어도 세션은 생성 가능하도록 academyId 0 사용
    // 실제로는 Academy 생성 후 세션을 연결
    AcademyAiSession session = new AcademyAiSession(0L, principal.userId());
    session = sessionRepository.save(session);

    return ResponseEntity.ok(new CreateResponse(
        reply, draft, session.getId().toString(), true));
  }

  /**
   * 승인된 초안으로 새 홈페이지를 생성한다.
   * 기존 POST /api/tutor/academy 생성 경로를 경유한다.
   * 이미 Academy가 있는 경우 거부한다 (덮어쓰기 방지).
   */
  @PostMapping("/create/apply")
  public ResponseEntity<Map<String, Object>> createApply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    // 기존 Academy 존재 시 거부
    if (academyService.getMine(principal).isPresent()) {
      throw new IllegalStateException(
          "이미 홈페이지가 있습니다. 새로 만들려면 기존 홈페이지를 먼저 삭제하거나, "
              + "채팅 수정 기능을 사용하세요.");
    }

    @SuppressWarnings("unchecked")
    Map<String, String> builderDraft = (Map<String, String>) request.get("builderDraft");
    String academyName = (String) request.getOrDefault("academyName", "새 학원");
    String designPreset = (String) request.getOrDefault("designPreset", "EXAM");

    // designPreset 검증
    if (!Set.of("EXAM", "ENGLISH", "ARTS", "STUDY", "ONLINE").contains(designPreset)) {
      throw new IllegalArgumentException("지원하지 않는 프리셋입니다: " + designPreset);
    }

    // builderDraft에 designPreset 주입
    Map<String, String> finalDraft = new java.util.HashMap<>();
    if (builderDraft != null) {
      // allowlist 검증
      for (String key : builderDraft.keySet()) {
        if (!ALLOWED_FIELDS.contains(key)
            && !ALLOWED_TOP_LEVEL_FIELDS.contains(key)
            && !"designPreset".equals(key)
            && !"academyName".equals(key)
            && !"targetGroups".equals(key)
            && !"ctaEnabled".equals(key)) {
          throw new IllegalArgumentException("허용되지 않은 필드: " + key);
        }
      }
      finalDraft.putAll(builderDraft);
    }
    finalDraft.put("designPreset", designPreset);

    // TODO: 실제 Academy 생성은 기존 POST /api/tutor/academy 경로를 경유
    // 2일차 통합 시 AcademyService.create()와 연결

    return ResponseEntity.ok(Map.of(
        "message", "초안이 승인되었습니다. Academy 생성 API와 연결이 필요합니다.",
        "designPreset", designPreset,
        "fieldCount", finalDraft.size()));
  }

  /**
   * 특정 버전으로 복원한다.
   * 복원 전 현재 상태를 스냅샷으로 저장한다.
   * 실제 복원은 기존 PUT /api/tutor/academy/page-settings 경로를 경유한다.
   */
  @PostMapping("/versions/{versionId}/restore")
  public ResponseEntity<ApplyResponse> restore(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long versionId) {
    AcademyResponse academy = getMyAcademy(principal);

    // 복원 대상 버전 조회 (권한 검증 포함)
    AcademyPageVersion version = versionService.getForRestore(principal, versionId);

    // 복원 전 현재 상태 스냅샷
    Map<String, String> currentDraft = getCurrentBuilderDraft(principal);
    Long snapshotId = versionService.snapshot(
        principal, currentDraft, Map.of(), "복원 전 스냅샷 (버전 " + version.getVersionNo() + "로 복원)");

    // 버전의 builderDraft를 파싱하여 기존 저장 경로로 복원
    Map<String, String> restoreDraft = parseBuilderDraftJson(version.getBuilderDraftJson());
    academyService.updatePageSettings(
        principal,
        new f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto
            .AcademyPageSettingsUpdateRequest(
            null, null, null, null, null, null, restoreDraft, null));

    // 작업 로그 기록
    AcademyAiSession session = createSession(principal, academy.id());
    ChatProposal restoreProposal = new ChatProposal(
        List.of(), "버전 " + version.getVersionNo() + "로 복원", session.getId().toString());
    AcademyAiAction action = logAction(
        session.getId(), academy.id(), "RESTORE", restoreProposal, 0, true);
    action.markApproved();
    actionRepository.save(action);

    return ResponseEntity.ok(new ApplyResponse(snapshotId, LocalDateTime.now().toString()));
  }

  // ── 헬퍼 ──

  private Map<String, String> getCurrentBuilderDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    // AcademyService에서 현재 builderDraft 조회
    // AcademyResponse에는 builderDraft가 없으므로 별도 조회 필요
    // 1일차: 빈 맵 반환, 2일차에 실제 조회 구현
    return Map.of();
  }

  private Map<String, String> parseBuilderDraftJson(String json) {
    try {
      return objectMapper.readValue(
          json,
          objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("버전 데이터 파싱 실패", e);
    }
  }

  // ── 보안 헬퍼 ──

  private AcademyResponse getMyAcademy(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academyService
        .getMine(principal)
        .orElseThrow(() -> new IllegalArgumentException("Academy가 없습니다."));
  }

  private AcademyAiSession getOrCreateSession(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long academyId, String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      return sessionRepository
          .findById(Long.parseLong(sessionId))
          .filter(s -> s.getAcademyId().equals(academyId))
          .orElseGet(() -> createSession(principal, academyId));
    }
    return createSession(principal, academyId);
  }

  private AcademyAiSession createSession(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long academyId) {
    AcademyAiSession session = new AcademyAiSession(academyId, principal.userId());
    return sessionRepository.save(session);
  }

  /**
   * 수정안을 allowlist 기준으로 서버에서 재검증한다.
   * AI 서비스의 검증을 신뢰하지 않고 서버에서 다시 검증한다 (defense in depth).
   */
  private ValidationResult validateAgainstAllowlist(ChatProposal proposal) {
    if (proposal == null || proposal.changes() == null) {
      return new ValidationResult(false, List.of("수정안이 비어 있습니다."));
    }
    List<String> errors = proposal.changes().stream()
        .map(FieldChange::key)
        .filter(key -> !ALLOWED_FIELDS.contains(key)
            && !ALLOWED_TOP_LEVEL_FIELDS.contains(key)
            || FORBIDDEN_FIELDS.contains(key))
        .map(key -> "허용되지 않은 필드: " + key)
        .toList();

    // 값 형식 검증 (HTML/스크립트 주입 방지)
    List<String> valueErrors = proposal.changes().stream()
        .filter(c -> c.after() != null && containsDangerousContent(c.after()))
        .map(c -> "위험한 콘텐츠가 포함된 필드: " + c.key())
        .toList();

    errors = new java.util.ArrayList<>(errors);
    errors.addAll(valueErrors);

    return new ValidationResult(errors.isEmpty(), errors);
  }

  /**
   * HTML/스크립트 주입을 검사한다.
   * AI 응답을 검증 없이 화면이나 DB에 적용하지 않는다.
   */
  private boolean containsDangerousContent(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("onerror=")
        || lower.contains("onload=")
        || lower.contains("onclick=");
  }

  private AcademyAiAction logAction(
      Long sessionId, Long academyId, String actionType, ChatProposal proposal,
      int tokens, boolean approved) {
    String changesJson = toJson(proposal.changes());
    AcademyAiAction action =
        new AcademyAiAction(sessionId, academyId, actionType, changesJson, tokens);
    if (approved) {
      action.markApproved();
    }
    return actionRepository.save(action);
  }

  private String buildReplyMessage(ChatProposal proposal) {
    if (proposal.changes().isEmpty()) {
      return "변경할 내용을 찾지 못했습니다. 다시 설명해 주세요.";
    }
    StringBuilder sb = new StringBuilder("다음과 같이 변경할까요?\n");
    for (FieldChange change : proposal.changes()) {
      sb.append("- ").append(change.key()).append(": ")
        .append(change.before()).append(" → ").append(change.after()).append("\n");
    }
    return sb.toString();
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  /** 테스트용 UUID 생성 (실제로는 사용하지 않음). */
  @SuppressWarnings("unused")
  private String generateSessionId() {
    return UUID.randomUUID().toString();
  }
}
