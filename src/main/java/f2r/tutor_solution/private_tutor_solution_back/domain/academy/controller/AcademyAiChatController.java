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
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageVersion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiActionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAiSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiContextService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiCreateService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiApplyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAiIdempotencyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyPageVersionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

  /**
   * 생성 전용 transport 키 (A1).
   * /create에서 생성되어 /create/apply로 전달되며, P0 필드로 변환 후
   * builderDraft에는 저장하지 않는다.
   */
  static final Set<String> TRANSPORT_KEYS = Set.of(
      "academyName", "targetGroups", "ctaEnabled", "subjectIds", "regionLocation");

  private final AcademyAiChatService chatService;
  private final AcademyAiCreateService createService;
  private final AcademyAiContextService contextService;
  private final AcademyPageVersionService versionService;
  private final AcademyService academyService;
  private final AcademyAiSessionRepository sessionRepository;
  private final AcademyAiActionRepository actionRepository;
  private final AcademyAiIdempotencyService idempotencyService;
  private final AcademyAiApplyService applyService;
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
    AcademyAiSession session = getOrCreateSession(principal, academy.academyId(), request.sessionId());

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
    logAction(session.getId(), academy.academyId(), "CHAT", proposal, 0, false);
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
  /**
   * 고객 승인 후 수정안을 적용한다.
   *
   * <p>처리 순서 (A2·B2·B3):
   * <ol>
   *   <li>allowlist·값 사전 검증 (스냅샷 전에 실패해야 고아 버전이 남지 않는다)
   *   <li>원자적 적용: builderDraft 변경 + intro → 하나의 트랜잭션
   *   <li>적용 성공 후 적용 전 상태 스냅샷 저장 (복원용)
   *   <li>작업 로그 기록
   * </ol>
   *
   * <p>{@code intro}는 builderDraft가 아닌 Academy 본문 필드로 저장한다 (A2).
   */
  @PostMapping("/apply")
  public ResponseEntity<ApplyResponse> apply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ApplyRequest request) {
    AcademyResponse academy = getMyAcademy(principal);

    // 세션 검증 (본인 Academy의 세션인지 확인) — A4: 권한 위반은 403
    // F1: 생성 전 세션(academyId NULL)은 /apply에서 사용할 수 없음
    AcademyAiSession session = sessionRepository
        .findById(Long.parseLong(request.sessionId()))
        .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));
    if (session.getAcademyId() == null
        || !session.getAcademyId().equals(academy.academyId())) {
      throw new AccessDeniedException("다른 학원의 세션에 접근할 수 없습니다.");
    }

    // B2: null/중복 키 사전 검증
    validateConfirmedChanges(request.confirmedChanges());

    // 변경 사항 서버 재검증 (allowlist + 위험 콘텐츠)
    ChatProposal proposal = new ChatProposal(request.confirmedChanges(), "", request.sessionId());
    ValidationResult validation = validateAgainstAllowlist(proposal);
    if (!validation.valid()) {
      throw new IllegalArgumentException(
          "허용되지 않은 변경이 포함되어 있습니다: " + String.join(", ", validation.errors()));
    }

    // A2: intro 분리 — top-level 필드는 Academy 본문 경로로
    String introChange = null;
    Map<String, String> draftChanges = new java.util.HashMap<>();
    for (FieldChange change : request.confirmedChanges()) {
      if ("intro".equals(change.key())) {
        introChange = change.after();
      } else {
        draftChanges.put(change.key(), change.after());
      }
    }

    // B2: 값 길이·형식 사전 검증 (스냅샷 전)
    academyService.validateAiDraft(draftChanges);

    // 적용 전 현재 상태 조회 (스냅샷용, F2: intro 포함)
    Map<String, String> currentDraft = getCurrentBuilderDraft(principal);
    String currentIntro = getCurrentIntro(principal);

    // B3: 원자적 적용 (builderDraft + intro를 하나의 트랜잭션으로)
    Map<String, String> updatedDraft = new java.util.HashMap<>(currentDraft);
    updatedDraft.putAll(draftChanges);
    applyService.applyDraftChanges(principal, updatedDraft, introChange);

    // 적용 성공 후 스냅샷 저장 (복원용, F2: intro 포함)
    Long versionId = versionService.snapshot(
        principal, currentDraft, Map.of(), currentIntro, "AI 채팅 수정 적용 전");

    // 작업 로그 기록 (승인됨)
    AcademyAiAction action = logAction(
        session.getId(), academy.academyId(), "APPLY", proposal, 0, true);
    action.markApproved();
    actionRepository.save(action);

    return ResponseEntity.ok(new ApplyResponse(versionId, LocalDateTime.now().toString()));
  }

  /**
   * 승인된 변경 목록의 기본 무결성을 검증한다 (B2).
   * null/빈 키, 중복 키를 스냅샷·적용 전에 차단한다.
   */
  private void validateConfirmedChanges(java.util.List<FieldChange> changes) {
    if (changes == null || changes.isEmpty()) {
      throw new IllegalArgumentException("적용할 변경이 없습니다.");
    }
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (FieldChange change : changes) {
      if (change == null || change.key() == null || change.key().isBlank()) {
        throw new IllegalArgumentException("변경 키가 비어 있습니다.");
      }
      if (!seen.add(change.key())) {
        throw new IllegalArgumentException("중복된 변경 키입니다: " + change.key());
      }
    }
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

    // F1: 세션 생성 (추적용) - 승인 전에는 Academy가 없으므로 academyId는 NULL
    // 0L 같은 가짜 ID를 사용하지 않는다 (V1055 FK 위반 방지)
    AcademyAiSession session = new AcademyAiSession(null, String.valueOf(principal.userId()));
    session = sessionRepository.save(session);

    return ResponseEntity.ok(new CreateResponse(
        reply, draft, session.getId().toString(), true));
  }

  /**
   * 승인된 초안으로 새 홈페이지를 생성한다.
   * AcademyService.createDraft()를 경유한다 (DRAFT 상태).
   * 공개는 기존 P0 발행 절차로 별도 처리한다.
   *
   * <p>멱등성: idempotencyKey로 중복 적용을 방지한다.
   */
  @PostMapping("/create/apply")
  public ResponseEntity<Map<String, Object>> createApply(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody Map<String, Object> request) {
    String idempotencyKey = (String) request.get("idempotencyKey");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "idempotencyKey가 필요합니다.");
    }

    // 결함 수정: 409가 400보다 우선한다.
    // 동일 키 재요청 시 (예: 첫 호출 성공 후 재시도) 400이 아닌 409를 반환해야
    // 클라이언트가 멱등성으로 인식할 수 있다.
    // 따라서 Academy 존재 여부(400)보다 멱등성 키 선점(409)을 먼저 확인한다.
    AcademyAiIdempotency idempotencyRecord;
    try {
      idempotencyRecord = idempotencyService.claim(
          String.valueOf(principal.userId()), idempotencyKey);
    } catch (AcademyAiIdempotencyService.DuplicateIdempotencyKeyException e) {
      AcademyAiIdempotency rec = e.getRecord();
      if (rec.isCompleted() && rec.getAcademyId() != null) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "이미 처리된 요청입니다. 생성된 홈페이지 ID: " + rec.getAcademyId());
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "이미 처리 중인 요청입니다: " + idempotencyKey);
    }

    // 기존 Academy 존재 시 400 거부 (덮어쓰기 방지) - API 계약 준수
    // 멱등성 선점 후이므로, 첫 호출이 성공했다면 위에서 409로 처리됨
    if (academyService.getMine(principal).isPresent()) {
      idempotencyService.markFailed(idempotencyRecord.getId());
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "이미 홈페이지가 있습니다. 새로 만들려면 기존 홈페이지를 먼저 삭제하거나, "
              + "채팅 수정 기능을 사용하세요.");
    }

    // 결함 수정: 검증 400 발생 시 멱등성 키를 FAILED로 표시한다.
    // 그렇지 않으면 키가 PROCESSING에 30분간 남아 정상 재시도를 차단한다.
    try {
      return doCreateApply(principal, request, idempotencyKey, idempotencyRecord);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
        idempotencyService.markFailed(idempotencyRecord.getId());
      }
      throw e;
    }
  }

  /**
   * createApply 본문 (검증 + 생성).
   * 호출자는 ResponseStatusException(400) 시 markFailed를 처리한다.
   */
  private ResponseEntity<Map<String, Object>> doCreateApply(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, Object> request,
      String idempotencyKey,
      AcademyAiIdempotency idempotencyRecord) {

    @SuppressWarnings("unchecked")
    Map<String, String> builderDraft = (Map<String, String>) request.get("builderDraft");
    if (builderDraft == null) {
      builderDraft = Map.of();
    }
    String academyName = (String) request.getOrDefault("academyName", "");
    String designPreset = (String) request.getOrDefault("designPreset", "EXAM");
    String intro = (String) request.getOrDefault("intro", "");

    // designPreset 검증
    if (!Set.of("EXAM", "ENGLISH", "ARTS", "STUDY", "ONLINE").contains(designPreset)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "지원하지 않는 프리셋입니다: " + designPreset);
    }

    // A1: 생성 전용 transport 키와 builderDraft 저장 키를 분리한다.
    // transport 키는 P0 필드로 변환하고 builderDraft에는 넣지 않는다.
    String draftAcademyName = builderDraft.get("academyName");
    String finalName = !isBlank(academyName) ? academyName.trim()
        : (draftAcademyName != null ? draftAcademyName.trim() : "");
    if (finalName.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 이름은 필수입니다.");
    }
    // 결함 #4: 학원명 보안 검증 강화
    if (containsDangerousContent(finalName)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 이름에 허용되지 않은 내용이 포함되어 있습니다.");
    }
    if (finalName.length() > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 이름은 100자 이하로 입력해야 합니다.");
    }
    String draftIntro = builderDraft.get("intro");
    String finalIntro = intro != null ? intro.trim() : "";
    if (finalIntro.isEmpty() && draftIntro != null) {
      finalIntro = draftIntro.trim();
    }
    if (finalIntro.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 소개는 필수입니다.");
    }
    if (finalIntro.length() > 1000) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 소개는 1000자 이하로 입력해야 합니다.");
    }
    // 결함 #4: 소개글 보안 검증 강화
    if (containsDangerousContent(finalIntro)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "학원 소개에 허용되지 않은 내용이 포함되어 있습니다.");
    }

    // targetGroups: 한글("중학생") → TargetGroup enum 변환
    java.util.Set<f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup>
        targetGroups = parseTargetGroupsKo(builderDraft.get("targetGroups"));
    if (targetGroups.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "수업 대상을 확인할 수 없습니다. (예: 중학생)");
    }

    // subjectIds: transport 키 파싱
    java.util.Set<Integer> subjectIds = parseSubjectIds(builderDraft.get("subjectIds"));
    if (subjectIds.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "과목을 확인할 수 없습니다. 초안 생성 시 과목명을 포함해주세요.");
    }

    // regions: transport 키 regionLocation을 그대로 전달 (사용자 입력 원문)
    String regionLocation = builderDraft.get("regionLocation");
    java.util.List<f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest>
        regions = (regionLocation != null && !regionLocation.isBlank())
        ? java.util.List.of(
            new f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto
                .AcademyRegionRequest(regionLocation.trim(), ""))
        : java.util.List.of();

    // builderDraft allowlist 검증 + HTML 주입 방지 (transport 키 제외)
    Map<String, String> finalDraft = new java.util.HashMap<>();
    for (Map.Entry<String, String> entry : builderDraft.entrySet()) {
      String key = entry.getKey();
      if (TRANSPORT_KEYS.contains(key)) {
        continue;
      }
      if (ALLOWED_TOP_LEVEL_FIELDS.contains(key)) {
        // top-level 필드는 별도 경로로 처리하므로 builderDraft에 저장하지 않는다
        continue;
      }
      if (!ALLOWED_FIELDS.contains(key)
          && !"designPreset".equals(key)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "허용되지 않은 필드: " + key);
      }
      if (entry.getValue() != null && containsDangerousContent(entry.getValue())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "위험한 콘텐츠가 포함된 필드: " + key);
      }
      finalDraft.put(key, entry.getValue());
    }
    finalDraft.put("designPreset", designPreset);

    // 멱등성: 메서드 시작 부분에서 이미 선점됨 (409 우선).
    // 여기서는 선점된 레코드를 사용한다.
    final AcademyAiIdempotency idemRecord = idempotencyRecord;

    // AcademyPageSettings 생성
    f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings
        pageSettings =
        new f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity
            .AcademyPageSettings(
            null, null, null, null, null, null, finalDraft, null);

    // AcademyUpsertRequest 생성 (A1: 실제 값 전달)
    f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest
        upsertRequest =
        new f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto
            .AcademyUpsertRequest(
            finalName, null, finalIntro, subjectIds, targetGroups, regions, pageSettings);

    // 실제 생성 (DRAFT 상태, @Transactional로 부분 저장 방지)
    // 동시 요청 경쟁: tutor UNIQUE 제약 위반 시 409로 변환한다.
    final AcademyResponse created;
    try {
      created = academyService.createDraft(principal, upsertRequest, idempotencyKey);
    } catch (DataIntegrityViolationException e) {
      idempotencyService.markFailed(idemRecord.getId());
      // 원인 구분: 이미 홈페이지가 생겼다면(경쟁에서 짐) 409, 그 외는 원본 예외 유지
      if (academyService.getMine(principal).isPresent()) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "이미 홈페이지가 있습니다. 새로 만들려면 기존 홈페이지를 먼저 삭제하거나, "
                + "채팅 수정 기능을 사용하세요.");
      }
      throw e;
    } catch (RuntimeException e) {
      idempotencyService.markFailed(idemRecord.getId());
      throw e;
    }

    // 성공 기록 + 버전 스냅샷 저장
    idempotencyService.markCompleted(idemRecord.getId(), created.academyId());
    versionService.snapshot(principal, finalDraft, Map.of(), "AI 생성 홈페이지 최초 생성");

    return ResponseEntity.ok(Map.of(
        "academyId", created.academyId(),
        "designPreset", designPreset,
        "status", "DRAFT",
        "message", "홈페이지가 생성되었습니다. Builder에서 확인 후 발행하세요."));
  }

  /**
   * 특정 버전으로 복원한다.
   * 복원 전 현재 상태를 스냅샷으로 저장한다.
   * 실제 복원은 기존 PUT /api/tutor/academy/page-settings 경로를 경유한다.
   *
   * <p>F2: 버전의 intro도 함께 복원한다.
   * <p>복원 범위 보호: AI 허용 키(ALLOWED_FIELDS)만 복원 대상으로 하며,
   * P0 Builder에서 사용자가 직접 수정한 키(색상·구성 등)는 삭제하지 않고 보존한다.
   */
  @PostMapping("/versions/{versionId}/restore")
  public ResponseEntity<ApplyResponse> restore(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long versionId) {
    AcademyResponse academy = getMyAcademy(principal);

    // 복원 대상 버전 조회 (권한 검증 포함)
    AcademyPageVersion version = versionService.getForRestore(principal, versionId);

    // 복원 전 현재 상태 조회 (F2: intro 포함)
    Map<String, String> currentDraft = getCurrentBuilderDraft(principal);
    String currentIntro = getCurrentIntro(principal);

    // 버전의 builderDraft를 파싱하여 복원 맵을 구성한다 (A3).
    // 복원 범위 보호: AI 허용 키에 대해서만 null 표시 (삭제)하고,
    // P0 키는 현재 값을 유지한다 (삭제하지 않음).
    Map<String, String> restoreDraft = parseBuilderDraftJson(version.getBuilderDraftJson());
    Map<String, String> fullRestore = new java.util.HashMap<>(restoreDraft);
    for (String key : currentDraft.keySet()) {
      if (!restoreDraft.containsKey(key) && ALLOWED_FIELDS.contains(key)) {
        // AI 허용 키만 null로 표시 (삭제 대상)
        fullRestore.put(key, null);
      }
      // P0 키 (ALLOWED_FIELDS에 없음)는 fullRestore에 포함하지 않음 → 보존
    }

    // B3: 원자적 복원
    applyService.applyDraftChanges(principal, fullRestore, null);

    // F2: intro 복원 (버전에 저장된 intro가 있으면)
    if (version.getIntro() != null) {
      academyService.updateIntro(principal, version.getIntro());
    }

    // 복원 성공 후 복원 전 상태 스냅샷 (되돌리기용, F2: intro 포함)
    Long snapshotId = versionService.snapshot(
        principal, currentDraft, Map.of(), currentIntro,
        "복원 전 스냅샷 (버전 " + version.getVersionNo() + "로 복원)");

    // 작업 로그 기록
    AcademyAiSession session = createSession(principal, academy.academyId());
    ChatProposal restoreProposal = new ChatProposal(
        List.of(), "버전 " + version.getVersionNo() + "로 복원", session.getId().toString());
    AcademyAiAction action = logAction(
        session.getId(), academy.academyId(), "RESTORE", restoreProposal, 0, true);
    action.markApproved();
    actionRepository.save(action);

    return ResponseEntity.ok(new ApplyResponse(snapshotId, LocalDateTime.now().toString()));
  }

  // ── 헬퍼 ──

  /**
   * 현재 저장된 builderDraft를 반환한다 (A3).
   * AcademyResponse.pageSettings()는 JSON 컬럼이므로 지연 로딩 문제가 없다.
   */
  private Map<String, String> getCurrentBuilderDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = getMyAcademy(principal);
    if (academy.pageSettings() == null || academy.pageSettings().builderDraft() == null) {
      return Map.of();
    }
    return new java.util.HashMap<>(academy.pageSettings().builderDraft());
  }

  /**
   * F2: 현재 Academy의 top-level intro를 반환한다.
   */
  private String getCurrentIntro(
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    AcademyResponse academy = getMyAcademy(principal);
    return academy.intro();
  }

  /**
   * 한글 대상 문자열을 TargetGroup enum 집합으로 변환한다 (A1).
   * 예: "중학생" → {MIDDLE}, "초등,중등" → {ELEMENTARY, MIDDLE}
   */
  static java.util.Set<f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup>
      parseTargetGroupsKo(String ko) {
    java.util.Set<f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup>
        result = java.util.EnumSet.noneOf(
            f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.class);
    if (ko == null || ko.isBlank()) {
      return result;
    }
    String t = ko.replaceAll("\\s+", "");
    if (t.contains("초등")) {
      result.add(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.ELEMENTARY);
    }
    if (t.contains("중학") || t.contains("중학생")) {
      result.add(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.MIDDLE);
    }
    if (t.contains("고등") || t.contains("고등학생")) {
      result.add(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.HIGH);
    }
    if (t.contains("대학")) {
      result.add(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.UNIVERSITY);
    }
    if (t.contains("성인")) {
      result.add(f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup.ADULT);
    }
    return result;
  }

  /** transport 키 subjectIds("1,2")를 파싱한다 (A1). */
  static java.util.Set<Integer> parseSubjectIds(String raw) {
    java.util.Set<Integer> result = new java.util.HashSet<>();
    if (raw == null || raw.isBlank()) {
      return result;
    }
    for (String part : raw.split(",")) {
      try {
        result.add(Integer.parseInt(part.trim()));
      } catch (NumberFormatException e) {
        // 무시: 빈 집합이면 400으로 처리됨
      }
    }
    return result;
  }

  static boolean isBlank(String s) {
    return s == null || s.isBlank();
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
          // F1: academyId NULL 세션은 매칭되지 않음 (생성 전 세션 보호)
          .filter(s -> academyId != null && academyId.equals(s.getAcademyId()))
          .orElseGet(() -> createSession(principal, academyId));
    }
    return createSession(principal, academyId);
  }

  private AcademyAiSession createSession(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long academyId) {
    AcademyAiSession session = new AcademyAiSession(academyId, String.valueOf(principal.userId()));
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
  /**
   * 위험한 콘텐츠 검사 (B5).
   * {@code onerror =} 같은 공백 우회를 막기 위해 속성명은 {@code =} 없이 검사한다.
   */
  private boolean containsDangerousContent(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script")
        || lower.contains("<iframe")
        || lower.contains("<svg")
        || lower.contains("<object")
        || lower.contains("<embed")
        || lower.contains("<link")
        || lower.contains("javascript:")
        || lower.contains("vbscript:")
        || lower.contains("data:text/html")
        || lower.contains("onerror")
        || lower.contains("onload")
        || lower.contains("onclick")
        || lower.contains("onmouseover");
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
