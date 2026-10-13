package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAssistantMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAssistantMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.WorkingCopyConflictException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiCompletion;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AiProvider.AiTaskLimits;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Applied;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Change;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Op;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Plan;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Snapshot;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AssistantPatchApplier.Undo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 수정 턴(2-C 핵심). 사용자의 자연어 수정 요청을 patch(연산 목록)로 해석하고, 서버에서 검증한 뒤 <b>비공개 작업본에만</b> 적용한다.
 *
 * <ul>
 *   <li>해석: Muse Spark → Qwen 폴백({@link AiProvider}) 구조화 출력(JSON). 실패·형식 오류면 규칙 해석기로 폴백한다.
 *   <li>적용: {@link AssistantPatchApplier}가 허용 목록으로 거른 뒤 {@link AcademyWorkingCopyService#save}로만 저장한다.
 *       공개 데이터를 직접 쓰는 기존 AI 적용 경로({@code AcademyAiApplyService})는 쓰지 않는다.
 *   <li>되돌리기: 수정 한 건마다 이전 값을 대화 기록(payload)에 남겨 여러 단계로 되돌린다. 게시하면 그 이전 수정은 되돌리기 대상에서 빠진다.
 *   <li>AI 호출 동안 DB 트랜잭션을 잡지 않는다(읽기 → AI → 쓰기 순서). 오류가 나도 작업본은 그대로다.
 * </ul>
 */
@Service
public class AcademyAssistantEditService {

  private static final Logger log = LoggerFactory.getLogger(AcademyAssistantEditService.class);

  static final String KIND_EDIT = "EDIT";
  static final String KIND_UNDO = "UNDO";
  static final String KIND_PUBLISHED = "PUBLISHED";
  /** 버전 복원: 이전 수정의 되돌리기 정보가 더는 맞지 않으므로 게시처럼 스택을 비운다. */
  static final String KIND_RESTORED = "RESTORED";
  private static final int AI_MAX_OUTPUT = 3000;
  private static final int MAX_REPLY = 300;

  /** AI 호출을 서버가 끊는 기본 한도(초). 느린 응답이 요청 스레드를 오래 잡지 않도록 한다. 0이면 제한 없음. */
  static final long DEFAULT_AI_TIMEOUT_SECONDS = 60;

  /** AI 호출 전용 스레드(데몬). 한도를 넘기면 취소(인터럽트)한다. */
  private static final ExecutorService AI_EXECUTOR = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "assistant-ai");
    t.setDaemon(true);
    return t;
  });

  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopyService;
  private final AcademyAssistantMessageRepository messages;
  private final AiProvider aiProvider;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate tx;
  private final long aiTimeoutSeconds;

  @Autowired
  public AcademyAssistantEditService(
      AcademyService academyService, AcademyWorkingCopyService workingCopyService,
      AcademyAssistantMessageRepository messages, AiProvider aiProvider, ObjectMapper objectMapper,
      PlatformTransactionManager txManager,
      @Value("${buildit.assistant.ai-timeout-seconds:" + DEFAULT_AI_TIMEOUT_SECONDS + "}") long aiTimeoutSeconds) {
    this.academyService = academyService;
    this.workingCopyService = workingCopyService;
    this.messages = messages;
    this.aiProvider = aiProvider;
    this.objectMapper = objectMapper;
    this.tx = new TransactionTemplate(txManager);
    this.aiTimeoutSeconds = aiTimeoutSeconds;
  }

  AcademyAssistantEditService(
      AcademyService academyService, AcademyWorkingCopyService workingCopyService,
      AcademyAssistantMessageRepository messages, AiProvider aiProvider, ObjectMapper objectMapper,
      PlatformTransactionManager txManager) {
    this(academyService, workingCopyService, messages, aiProvider, objectMapper, txManager, DEFAULT_AI_TIMEOUT_SECONDS);
  }

  /**
   * AI 응답이 서버 한도를 넘겼다. 사용자 입력·작업본·공개본·대화 기록은 바뀌지 않았고(쓰기 전에 던진다),
   * 정상 수정으로 보이지 않도록 컨트롤러가 504 {@code AI_TIMEOUT}으로 알린다. 규칙 해석으로 대신 처리하지 않는다.
   */
  public static class AiTimeoutException extends RuntimeException {
    private final long limitSeconds;

    public AiTimeoutException(long limitSeconds) {
      super("AI 응답이 " + limitSeconds + "초 안에 오지 않아 이번 요청은 처리하지 못했어요. 입력과 작업본은 그대로예요. 잠시 후 다시 시도해 주세요.");
      this.limitSeconds = limitSeconds;
    }

    public long getLimitSeconds() {
      return limitSeconds;
    }
  }

  /** 수정/되돌리기 결과. failed=true면 작업본은 바뀌지 않았다(reply가 이유를 안내한다). */
  public record EditOutcome(
      String reply, List<Change> changes, List<String> skipped, String engine, long aiMs,
      boolean failed, Long revision, int undoDepth, String question) {}

  // ───────────────────────── 수정 ─────────────────────────

  public EditOutcome edit(
      JwtTokenProvider.AuthenticatedPrincipal principal, String text, Long expectedRevision) {
    AcademyResponse academy = academyService.getMine(principal)
        .orElseThrow(() -> new IllegalArgumentException("홈페이지를 먼저 만들어 주세요."));
    Long academyId = academy.academyId();

    // 1) 읽기
    State state = tx.execute(s -> workingCopyService.open(principal));
    requireRevision(state, expectedRevision);
    Snapshot snap = snapshotOf(state, imageSlotsOf(academy, workingCopyService.stagedMedia(principal)));

    // 2) AI 해석 (트랜잭션 밖)
    Interpretation interp = interpret(text, academy, snap);
    if (interp.aiTimedOut()) {
      throw new AiTimeoutException(aiTimeoutSeconds);
    }

    // 3) 검증
    Plan plan = AssistantPatchApplier.plan(interp.ops(), snap, text);
    if (plan.accepted().isEmpty()) {
      String reply = noChangeReply(interp, plan);
      tx.executeWithoutResult(s -> {
        remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, text, null);
        remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, AcademyAssistantService.KIND_TEXT, reply, null);
      });
      logOutcome(interp, plan, 0, true);
      return new EditOutcome(reply, List.of(), plan.skipped(), interp.engine(), interp.ms(),
          interp.aiFailed() && interp.question() == null, state.revision(), undoDepth(academyId), interp.question());
    }

    // 4) 쓰기 (작업본에만)
    try {
      EditOutcome out = tx.execute(s -> {
        State fresh = workingCopyService.open(principal);
        requireRevision(fresh, state.revision());
        Map<String, String> stagedBefore = workingCopyService.stagedMedia(principal);
        Applied applied = AssistantPatchApplier.apply(
            snapshotOf(fresh, imageSlotsOf(academy, stagedBefore)), plan.accepted());
        if (applied.isEmpty()) {
          return null;
        }
        State saved = hasContent(applied)
            ? workingCopyService.save(principal, fresh.revision(), applied.intro(), toPatch(applied))
            : fresh;
        // 사진 삭제는 공개 슬롯이 아니라 작업본의 이미지 예약만 바꾼다(되돌리기용으로 이전 예약 값을 남긴다).
        Map<String, Object> mediaUndo = new java.util.LinkedHashMap<>();
        for (String slot : applied.mediaClears()) {
          mediaUndo.put(slot, stagedBefore.get(slot));
          saved = workingCopyService.stageMedia(principal, saved.revision(), slot, "");
        }
        String reply = editReply(interp, applied.changes(), plan.skipped());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("changes", applied.changes());
        payload.put("skipped", plan.skipped());
        payload.put("undo", applied.undo());
        if (!mediaUndo.isEmpty()) payload.put("mediaUndo", mediaUndo);
        payload.put("revisionAfter", saved.revision());
        payload.put("engine", interp.engine());
        payload.put("aiMs", interp.ms());
        remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, text, null);
        remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, KIND_EDIT, reply, toJson(payload));
        return new EditOutcome(reply, applied.changes(), plan.skipped(), interp.engine(), interp.ms(),
            false, saved.revision(), undoDepth(academyId), null);
      });
      if (out == null) {
        String reply = "이미 그렇게 적용되어 있어요. 다른 방향으로 바꿔 볼까요?";
        tx.executeWithoutResult(s -> {
          remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, text, null);
          remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, AcademyAssistantService.KIND_TEXT, reply, null);
        });
        logOutcome(interp, plan, 0, true);
        return new EditOutcome(reply, List.of(), plan.skipped(), interp.engine(), interp.ms(), false,
            state.revision(), undoDepth(academyId), null);
      }
      logOutcome(interp, plan, out.changes().size(), false);
      return out;
    } catch (IllegalArgumentException e) {
      // 저장 규칙 검증 실패: 트랜잭션이 롤백되어 작업본은 그대로다.
      String reply = "그 변경은 저장 규칙에 맞지 않아 적용하지 않았어요. 작업본은 그대로예요. (" + e.getMessage() + ")";
      tx.executeWithoutResult(s -> {
        remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, text, null);
        remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, AcademyAssistantService.KIND_TEXT, reply, null);
      });
      logOutcome(interp, plan, 0, true);
      return new EditOutcome(reply, List.of(), plan.skipped(), interp.engine(), interp.ms(), true,
          state.revision(), undoDepth(academyId), null);
    }
  }

  // ───────────────────────── 사진 올리기(S3) ─────────────────────────

  /**
   * 이미 저장소에 올라간 사진(storageKey)을 작업본의 이미지 예약으로 건다. 공개 슬롯은 게시 전까지 그대로다.
   * 사용자가 직접 올린 사진만 쓴다 — 에이전트는 이미지를 만들거나 찾아 오지 않는다. 되돌리기 대상(EDIT)으로 기록한다.
   */
  public EditOutcome stageUploadedMedia(
      JwtTokenProvider.AuthenticatedPrincipal principal, String slot, String storageKey, Long expectedRevision) {
    String[] meta = AssistantDesignCatalog.IMAGE_SLOTS.get(slot);
    if (meta == null) throw new IllegalArgumentException("올릴 수 없는 사진 자리예요: " + slot);
    AcademyResponse academy = academyService.getMine(principal)
        .orElseThrow(() -> new IllegalArgumentException("홈페이지를 먼저 만들어 주세요."));
    Long academyId = academy.academyId();
    return tx.execute(s -> {
      State fresh = workingCopyService.open(principal);
      requireRevision(fresh, expectedRevision);
      Map<String, String> stagedBefore = workingCopyService.stagedMedia(principal);
      boolean had = imageSlotsOf(academy, stagedBefore).contains(slot);
      State saved = workingCopyService.stageMedia(principal, fresh.revision(), slot, storageKey);
      AssistantPatchApplier.Change change = new AssistantPatchApplier.Change(
          (had ? "사진 교체: " : "사진 추가: ") + meta[0], had ? "기존 사진" : "(없음)", "올리신 사진(미리보기에만 반영)", meta[1]);
      String reply = meta[0] + "을(를) " + (had ? "교체" : "추가") + "했어요. 미리보기에서 확인해 보세요. 게시하기 전에는 공개되지 않아요.";
      Map<String, Object> mediaUndo = new java.util.LinkedHashMap<>();
      mediaUndo.put(slot, stagedBefore.get(slot));
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("changes", List.of(change));
      payload.put("skipped", List.of());
      payload.put("undo", new Undo(false, null, Map.of(), null, null));
      payload.put("mediaUndo", mediaUndo);
      payload.put("revisionAfter", saved.revision());
      payload.put("engine", "UPLOAD");
      remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, "사진 올리기: " + meta[0], null);
      remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, KIND_EDIT, reply, toJson(payload));
      return new EditOutcome(reply, List.of(change), List.of(), "UPLOAD", 0, false, saved.revision(),
          undoDepth(academyId), null);
    });
  }

  // ───────────────────────── 되돌리기 ─────────────────────────

  public EditOutcome undo(JwtTokenProvider.AuthenticatedPrincipal principal, Long expectedRevision) {
    AcademyResponse academy = academyService.getMine(principal)
        .orElseThrow(() -> new IllegalArgumentException("홈페이지를 먼저 만들어 주세요."));
    Long academyId = academy.academyId();
    return tx.execute(s -> {
      State fresh = workingCopyService.open(principal);
      requireRevision(fresh, expectedRevision);
      UndoStack stack = stackOf(academyId);
      if (stack.topEdit == null) {
        return new EditOutcome("되돌릴 수정이 없어요. (게시한 뒤의 수정만 되돌릴 수 있어요)", List.of(), List.of(),
            "NONE", 0, true, fresh.revision(), 0, null);
      }
      if (stack.lastRevision == null || stack.lastRevision != fresh.revision()) {
        return new EditOutcome("작업본이 다른 곳에서 바뀌어 안전하게 되돌릴 수 없어요. 화면을 새로고침해 주세요.", List.of(), List.of(),
            "NONE", 0, true, fresh.revision(), stack.depth, null);
      }
      Undo undo = readUndo(stack.topEdit);
      if (undo == null) {
        return new EditOutcome("되돌리기 정보를 읽을 수 없어요.", List.of(), List.of(), "NONE", 0, true,
            fresh.revision(), stack.depth, null);
      }
      Applied inv = AssistantPatchApplier.inverse(
          snapshotOf(fresh, imageSlotsOf(academy, workingCopyService.stagedMedia(principal))), undo);
      State saved = hasContent(inv)
          ? workingCopyService.save(principal, fresh.revision(), inv.intro(), toPatch(inv))
          : fresh;
      // 사진 예약도 되돌린다(이전 예약 값이 없었으면 예약 자체를 지워 공개본 그대로로 돌아간다).
      List<AssistantPatchApplier.Change> changes = new ArrayList<>(inv.changes());
      JsonNode mediaUndo = payloadOf(stack.topEdit) == null ? null : payloadOf(stack.topEdit).get("mediaUndo");
      if (mediaUndo != null && mediaUndo.isObject()) {
        var it = mediaUndo.fields();
        while (it.hasNext()) {
          var e = it.next();
          saved = workingCopyService.stageMedia(
              principal, saved.revision(), e.getKey(), e.getValue().isNull() ? null : e.getValue().asText());
          String[] meta = AssistantDesignCatalog.IMAGE_SLOTS.get(e.getKey());
          changes.add(new AssistantPatchApplier.Change(
              "사진: " + (meta == null ? e.getKey() : meta[0]), "변경됨", "이전 상태로 복원", meta == null ? null : meta[1]));
        }
      }
      String reply = "이전 수정을 되돌렸어요.";
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("undoes", stack.topEdit.getMessageId());
      payload.put("changes", changes);
      payload.put("revisionAfter", saved.revision());
      remember(academyId, AcademyAssistantService.ROLE_USER, AcademyAssistantService.KIND_TEXT, "되돌리기", null);
      remember(academyId, AcademyAssistantService.ROLE_ASSISTANT, KIND_UNDO, reply, toJson(payload));
      return new EditOutcome(reply, changes, List.of(), "UNDO", 0, false, saved.revision(), stack.depth - 1, null);
    });
  }

  /** 되돌릴 수 있는 수정 단계 수(마지막 게시 이후, 아직 되돌리지 않은 수정). */
  public int undoDepth(Long academyId) {
    return stackOf(academyId).depth;
  }

  // ───────────────────────── 해석 ─────────────────────────

  record Interpretation(
      List<Op> ops, String reply, String question, String engine, long ms, boolean aiFailed, boolean parseOk,
      boolean aiTimedOut) {}

  Interpretation interpret(String text, AcademyResponse academy, Snapshot snap) {
    boolean aiFailed = false;
    boolean timedOut = false;
    long ms = 0;
    if (aiProvider != null && aiProvider.isReady()) {
      long started = System.nanoTime();
      AiCompletion c;
      try {
        c = completeWithDeadline(buildPrompt(text, academy, snap));
      } catch (AiDeadlineExceeded e) {
        timedOut = true;
        c = AiCompletion.failed("timeout");
      } catch (RuntimeException e) {
        c = AiCompletion.failed("provider exception: " + e.getClass().getSimpleName());
      }
      ms = (System.nanoTime() - started) / 1_000_000;
      if (timedOut) {
        log.warn("assistant-edit ai-timeout afterMs={} limitSeconds={}", ms, aiTimeoutSeconds);
        // 시간 초과는 규칙 해석으로 대신 처리하지 않는다(성공처럼 보이는 수정 금지). 호출부가 504로 알린다.
        return new Interpretation(List.of(), null, null, "TIMEOUT", ms, true, false, true);
      }
      if (c.ok()) {
        Parsed parsed = parse(c.text(), objectMapper);
        if (parsed != null && (!parsed.ops().isEmpty() || parsed.question() != null)) {
          return new Interpretation(parsed.ops(), parsed.reply(), parsed.question(), "AI:" + aiProvider.name(), ms, false, true, false);
        }
        aiFailed = true; // 형식 오류 또는 빈 응답 → 규칙 폴백
      } else {
        aiFailed = true;
      }
    } else {
      aiFailed = true;
    }
    AssistantRuleInterpreter.Result r = AssistantRuleInterpreter.interpret(text, snap.draft());
    return new Interpretation(r.ops(), r.reply(), r.question(), "RULE", ms, aiFailed, false, timedOut);
  }

  /** AI 호출이 서버 한도를 넘겼다. 작업본은 건드리지 않고 규칙 해석으로 넘어간다. */
  private static final class AiDeadlineExceeded extends RuntimeException {
    AiDeadlineExceeded() {
      super("assistant ai deadline exceeded", null, false, false);
    }
  }

  /** 한도(초) 안에서만 AI 응답을 기다린다. 넘기면 호출을 취소(인터럽트)하고 {@link AiDeadlineExceeded}를 던진다. */
  private AiCompletion completeWithDeadline(String prompt) {
    AiTaskLimits limits = new AiTaskLimits(AI_MAX_OUTPUT);
    if (aiTimeoutSeconds <= 0) {
      return aiProvider.complete(prompt, limits);
    }
    // 요청 스레드의 인증 정보를 AI 스레드에 넘긴다. Muse Spark 실패 시 이어지는 Wiki Router 폴백이 로그인 주체로 서명하기 때문이다.
    Callable<AiCompletion> task = () -> aiProvider.complete(prompt, limits);
    Future<AiCompletion> future = AI_EXECUTOR.submit(
        DelegatingSecurityContextCallable.create(task, SecurityContextHolder.getContext()));
    try {
      return future.get(aiTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new AiDeadlineExceeded();
    } catch (ExecutionException e) {
      return AiCompletion.failed("provider exception: " + e.getCause().getClass().getSimpleName());
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      return AiCompletion.failed("interrupted");
    }
  }

  record Parsed(List<Op> ops, String reply, String question) {}

  /** LLM 응답(코드 펜스·앞뒤 설명 허용)에서 JSON을 꺼낸다. 형식이 틀리면 null. */
  static Parsed parse(String completion, ObjectMapper om) {
    if (completion == null || completion.isBlank()) return null;
    String s = completion.trim();
    int a = s.indexOf('{');
    int b = s.lastIndexOf('}');
    if (a < 0 || b <= a) return null;
    try {
      JsonNode root = om.readTree(s.substring(a, b + 1));
      if (root == null || !root.isObject()) return null;
      List<Op> ops = new ArrayList<>();
      JsonNode arr = root.get("ops");
      if (arr != null && arr.isArray()) {
        for (JsonNode n : arr) {
          if (!n.isObject()) continue;
          ops.add(new Op(txt(n, "op"), txt(n, "key"), txt(n, "value"), txt(n, "id"), txt(n, "before"), txt(n, "after")));
        }
      } else if (arr != null && !arr.isNull()) {
        return null;
      }
      String reply = AssistantPatchApplier.cleanText(txt(root, "reply"));
      if (reply.length() > MAX_REPLY) reply = reply.substring(0, MAX_REPLY);
      String question = AssistantPatchApplier.cleanText(txt(root, "question"));
      return new Parsed(ops, reply.isEmpty() ? null : reply, question.isEmpty() ? null : question);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private static String txt(JsonNode n, String f) {
    JsonNode v = n.get(f);
    return v == null || v.isNull() ? null : v.asText();
  }

  /** 모델에 보낼 프롬프트. 현재 작업본 상태 + 허용 연산·값 + 규칙 + 사용자 요청. */
  String buildPrompt(String userText, AcademyResponse academy, Snapshot snap) {
    StringBuilder sb = new StringBuilder(4000);
    sb.append("너는 학원 홈페이지 편집 도우미다. 사용자의 자연어 수정 요청을 아래 '연산(ops)'으로만 바꿔서 JSON 한 개로 답한다.\n");
    sb.append("규칙:\n");
    sb.append("- JSON 외의 글(설명·코드펜스)을 출력하지 않는다.\n");
    sb.append("- 허용된 연산과 값만 쓴다. 허용 목록에 없는 값은 쓰지 않는다.\n");
    sb.append("- 요청이 [디자인 자산] 목록의 스타일·효과와 맞으면 코드를 새로 만들지 말고 {\"op\":\"applyAsset\",\"id\":\"자산id\"}를 우선 쓴다. 자산 적용 뒤 사용자가 따로 말한 색·문구만 개별 연산으로 덧붙인다. 목록에 없는 id는 쓰지 않는다.\n");
    sb.append("- \"더 고급스럽게\", \"밝고 친근하게\" 같은 포괄적 요청은 setPreset + setTypography + setHeroLayout + setCardStyle (+ 필요 시 setColor)을 함께 쓰는 복합 변경으로 처리한다.\n");
    sb.append("- 사용자가 말하지 않은 전화번호·주소·수강료·강사·경력·합격 실적·숫자를 절대 만들어 넣지 않는다. 모르면 question으로 되묻는다.\n");
    sb.append("- 문구 변경(setText/setIntro)은 과장 없이 자연스러운 한국어로, 짧게(항목당 120자 이하, 소개 문단 600자 이하) 쓴다.\n");
    sb.append("- 한 번에 최대 ").append(AssistantPatchApplier.MAX_OPS).append("개 연산. 의도를 알 수 없으면 ops는 비우고 question에 한 문장으로 되묻는다.\n");
    sb.append("- reply는 사용자에게 보여 줄 1~2문장의 한국어 안내(무엇을 어떻게 바꿨는지).\n\n");

    sb.append("[출력 형식]\n");
    sb.append("{\"reply\":\"...\",\"question\":null,\"ops\":[\n");
    sb.append("  {\"op\":\"applyAsset\",\"id\":\"tmpl-minimal-landing\"},\n");
    sb.append("  {\"op\":\"setMotion\",\"value\":\"fade\"},\n");
    sb.append("  {\"op\":\"setPreset\",\"value\":\"DARK\"},\n");
    sb.append("  {\"op\":\"setTypography\",\"value\":\"PREMIUM\"},\n");
    sb.append("  {\"op\":\"setHeroLayout\",\"value\":\"center\"},\n");
    sb.append("  {\"op\":\"setCardStyle\",\"value\":\"minimal\"},\n");
    sb.append("  {\"op\":\"setColor\",\"key\":\"colorPrimary\",\"value\":\"#0f4c81\"},\n");
    sb.append("  {\"op\":\"setFont\",\"key\":\"headingFont\",\"value\":\"NOTO_SERIF_KR\"},\n");
    sb.append("  {\"op\":\"setText\",\"key\":\"introHeading\",\"value\":\"...\"},\n");
    sb.append("  {\"op\":\"setIntro\",\"value\":\"...\"},\n");
    sb.append("  {\"op\":\"showSection\",\"id\":\"sec-lessons\"}, {\"op\":\"hideSection\",\"id\":\"sec-videos\"},\n");
    sb.append("  {\"op\":\"moveSection\",\"id\":\"sec-cta\",\"value\":\"first\"},  // value: first|last, 또는 before/after에 대상 섹션 id\n");
    sb.append("  {\"op\":\"clearImage\",\"value\":\"principal\"}  // 이미 올라가 있는 사진 비우기만 가능\n");
    sb.append("]}\n\n");

    sb.append("[허용 값]\n");
    sb.append("setPreset: ");
    AssistantDesignCatalog.PRESETS.forEach((k, v) -> sb.append(k).append("=").append(v[0]).append("(").append(v[1]).append("); "));
    sb.append("\nsetTypography: ");
    AssistantDesignCatalog.TYPOGRAPHY.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
    sb.append("\nsetHeroLayout: center(가운데)|split(좌우 분할)|visual(이미지 중심)|gradient(그라데이션)");
    sb.append("\nsetCardStyle: bordered(테두리)|rounded(둥근)|media(이미지)|minimal(미니멀)");
    sb.append("\nsetMotion: ");
    AssistantDesignCatalog.MOTIONS.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
    sb.append("\nsetFont key: headingFont|bodyFont, value: PRETENDARD|SUIT|NOTO_SANS_KR|NOTO_SERIF_KR");
    sb.append("\nsetColor key(값은 #RRGGBB): ");
    AssistantDesignCatalog.COLOR_KEYS.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
    sb.append("\nsetText key: ");
    AssistantDesignCatalog.TEXT_KEYS.forEach((k, v) -> sb.append(k).append("=").append(v[0]).append("; "));
    sb.append("\nclearImage value: ");
    AssistantDesignCatalog.IMAGE_SLOTS.forEach((k, v) -> sb.append(k).append("=").append(v[0]).append("; "));
    sb.append("(사진을 새로 올리거나 만들어 주는 연산은 없다. 새 사진은 사용자가 직접 올린 것만 쓰며, 요청 시 '사진 올리기 버튼으로 올려 달라'고 안내한다)");
    sb.append("\n섹션 id: ");
    AssistantDesignCatalog.SECTIONS.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
    sb.append("\n(sec-hero는 항상 맨 위, 바꿀 수 없음)\n\n");

    sb.append("[디자인 자산] (BuildIt2에서 검증한 값 묶음. 요청과 관련 높은 순)\n");
    for (AssistantAssetCatalog.Asset a : AssistantAssetCatalog.rankedActive(userText, academy.name())) {
      sb.append("- ").append(a.id()).append(" [").append(a.kind()).append("] ").append(a.label()).append(": ").append(a.summary()).append('\n');
    }
    sb.append('\n');

    sb.append("[현재 작업본]\n");
    sb.append("학원명: ").append(academy.name()).append('\n');
    if (academy.subjects() != null && !academy.subjects().isEmpty()) {
      sb.append("과목: ").append(academy.subjects().stream().map(x -> x.nameKo()).reduce((x, y) -> x + ", " + y).orElse("")).append('\n');
    }
    Map<String, String> d = snap.draft();
    sb.append("디자인: ").append(d.getOrDefault("designPreset", "(미지정)"))
        .append(" / 글꼴: ").append(d.getOrDefault("typographyPreset", "(프리셋 기본)"))
        .append(" / 상단: ").append(d.getOrDefault("heroLayout", "(프리셋 기본)"))
        .append(" / 카드: ").append(d.getOrDefault("cardStyle", "(프리셋 기본)"))
        .append(" / 등장 효과: ").append(d.getOrDefault("motionStyle", "(기본)"))
        .append(" / 적용한 자산: ").append(d.getOrDefault("designAsset", "(없음)")).append('\n');
    StringBuilder colors = new StringBuilder();
    AssistantDesignCatalog.COLOR_KEYS.keySet().forEach(k -> {
      if (d.get(k) != null) colors.append(k).append('=').append(d.get(k)).append(' ');
    });
    if (colors.length() > 0) sb.append("지정된 색: ").append(colors).append('\n');
    sb.append("섹션(위→아래): ");
    for (String id : AssistantPatchApplier.normalizedOrder(snap.order())) {
      if (!AssistantDesignCatalog.SECTIONS.containsKey(id)) continue;
      sb.append(id).append(AssistantPatchApplier.visible(snap.visibility(), id) ? "(표시)" : "(숨김)").append(' ');
    }
    sb.append('\n');
    sb.append("소개 문단: ").append(AssistantPatchApplier.shorten(snap.intro())).append('\n');
    for (String k : AssistantDesignCatalog.TEXT_KEYS.keySet()) {
      if (d.get(k) != null && !d.get(k).isBlank()) sb.append(k).append(": ").append(AssistantPatchApplier.shorten(d.get(k))).append('\n');
    }
    sb.append("\n[사용자 요청]\n\"\"\"\n").append(userText.replace("\"\"\"", "\"")).append("\n\"\"\"\n");
    return sb.toString();
  }

  // ───────────────────────── 내부 ─────────────────────────

  private String noChangeReply(Interpretation interp, Plan plan) {
    if (interp.question() != null) {
      return interp.aiFailed() && "RULE".equals(interp.engine())
          ? interp.question() + "\n(지금은 AI 해석을 쓸 수 없어 간단한 요청만 처리할 수 있어요. 작업본은 그대로예요.)"
          : interp.question();
    }
    StringBuilder sb = new StringBuilder();
    if (!plan.skipped().isEmpty()) {
      sb.append("요청을 적용하지 못했어요. 작업본은 그대로예요.\n");
      plan.skipped().forEach(x -> sb.append("· ").append(x).append('\n'));
      return sb.toString().trim();
    }
    if (interp.aiFailed() && interp.ops().isEmpty()) {
      return "AI가 요청을 해석하지 못했어요. 작업본은 그대로예요. 잠시 후 다시 시도하거나 "
          + "\"더 고급스럽게\", \"주색을 초록색으로\", \"수업 영상 섹션 숨겨 줘\"처럼 구체적으로 알려 주세요.";
    }
    return "적용할 변경을 찾지 못했어요. 작업본은 그대로예요. 어떻게 바꿀지 조금 더 구체적으로 알려 주세요.";
  }

  private String editReply(Interpretation interp, List<Change> changes, List<String> skipped) {
    StringBuilder sb = new StringBuilder();
    String lead = interp.reply() != null ? interp.reply() : changes.size() + "가지를 바꿨어요.";
    sb.append(lead);
    if (!skipped.isEmpty()) {
      sb.append("\n다만 일부는 적용하지 않았어요:");
      skipped.forEach(x -> sb.append("\n· ").append(x));
    }
    sb.append("\n미리보기에서 확인해 보세요. 마음에 들지 않으면 '되돌리기'를 눌러 주세요.");
    return sb.toString();
  }

  /** 지금 이미지가 있는 슬롯 = 공개 슬롯 + 예약된 교체 − 예약된 비우기. */
  private static java.util.Set<String> imageSlotsOf(AcademyResponse academy, Map<String, String> staged) {
    java.util.Set<String> slots = new java.util.HashSet<>(
        academy.heroMediaSlots() == null ? List.<String>of() : academy.heroMediaSlots());
    staged.forEach((slot, key) -> {
      if (key == null || key.isEmpty()) slots.remove(slot);
      else slots.add(slot);
    });
    return slots;
  }

  private static boolean hasContent(Applied a) {
    return a.intro() != null || !a.draftPatch().isEmpty() || a.visibility() != null || a.order() != null;
  }

  private static Snapshot snapshotOf(State state, java.util.Set<String> imageSlots) {
    AcademyPageSettings ps = state.pageSettings();
    return new Snapshot(
        state.intro(),
        ps == null ? null : ps.builderDraft(),
        ps == null ? null : ps.sectionVisibility(),
        ps == null ? null : ps.sectionOrder(),
        imageSlots);
  }

  private static AcademyPageSettingsUpdateRequest toPatch(Applied a) {
    return new AcademyPageSettingsUpdateRequest(
        null, null, null, null, a.visibility(), a.order(), null,
        a.draftPatch().isEmpty() ? null : a.draftPatch());
  }

  private static void requireRevision(State state, Long expected) {
    if (expected != null && state.revision() != expected) {
      throw new WorkingCopyConflictException(
          "REVISION_CONFLICT", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.");
    }
  }

  private void remember(Long academyId, String role, String kind, String content, String payload) {
    messages.save(new AcademyAssistantMessage(academyId, role, kind, content, payload));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("대화 부가 정보를 저장할 수 없습니다.", e);
    }
  }

  private Undo readUndo(AcademyAssistantMessage edit) {
    try {
      JsonNode n = objectMapper.readTree(edit.getPayloadJson()).get("undo");
      return n == null ? null : objectMapper.treeToValue(n, Undo.class);
    } catch (JsonProcessingException | RuntimeException e) {
      return null;
    }
  }

  /** 마지막 게시 이후의 수정 스택. lastRevision은 가장 최근 기록(생성·수정·되돌리기·게시)이 남긴 작업본 revision. */
  private static final class UndoStack {
    AcademyAssistantMessage topEdit;
    int depth;
    Long lastRevision;
  }

  private UndoStack stackOf(Long academyId) {
    List<AcademyAssistantMessage> all = messages.findByAcademyIdOrderByMessageIdAsc(academyId);
    List<AcademyAssistantMessage> stack = new ArrayList<>();
    Long last = null;
    for (AcademyAssistantMessage m : all) {
      JsonNode p = payloadOf(m);
      Long rev = null;
      if (p != null) {
        if (p.hasNonNull("revisionAfter")) rev = p.get("revisionAfter").asLong();
        else if (p.hasNonNull("revision")) rev = p.get("revision").asLong();
      }
      switch (m.getKind()) {
        case KIND_EDIT -> stack.add(m);
        case KIND_UNDO -> {
          long undone = p != null && p.hasNonNull("undoes") ? p.get("undoes").asLong() : -1;
          stack.removeIf(e -> e.getMessageId() == undone);
        }
        case KIND_PUBLISHED, KIND_RESTORED -> stack.clear();
        default -> { }
      }
      if (rev != null) last = rev;
    }
    UndoStack s = new UndoStack();
    s.depth = stack.size();
    s.topEdit = stack.isEmpty() ? null : stack.get(stack.size() - 1);
    s.lastRevision = last;
    return s;
  }

  private JsonNode payloadOf(AcademyAssistantMessage m) {
    if (m.getPayloadJson() == null || m.getPayloadJson().isBlank()) return null;
    try {
      return objectMapper.readTree(m.getPayloadJson());
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private void logOutcome(Interpretation i, Plan plan, int applied, boolean noChange) {
    log.info("assistant-edit engine={} aiMs={} aiFailed={} parseOk={} ops={} accepted={} skipped={} applied={} noChange={}",
        i.engine(), i.ms(), i.aiFailed(), i.parseOk(), i.ops() == null ? 0 : i.ops().size(),
        plan.accepted().size(), plan.skipped().size(), applied, noChange);
  }

  /** 테스트·측정용: 현재 AI 제공자가 쓸 수 있는지. */
  boolean aiReady() {
    return aiProvider != null && aiProvider.isReady();
  }

  Optional<String> providerName() {
    return aiProvider == null ? Optional.empty() : Optional.of(aiProvider.name());
  }
}
