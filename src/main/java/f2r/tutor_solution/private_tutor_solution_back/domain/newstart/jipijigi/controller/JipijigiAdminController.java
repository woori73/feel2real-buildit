package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiBoardService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiCommandService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiInventoryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiQuestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 지피지기 Agent ADMIN API (ROLE_ADMIN).
 * V1은 조사·초안·승인 워크플로만 제공하고, NAVER 실제 게시는 서버에서 상시 차단된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/newstart/jipijigi")
public class JipijigiAdminController {

    private final JipijigiCommandService commandService;
    private final JipijigiQuestionService questionService;
    private final JipijigiWorkflowService workflowService;
    private final JipijigiBoardService boardService;
    private final JipijigiInventoryService inventoryService;

    // ── inventory / boards ────────────────────────────────────────────
    @GetMapping("/inventory")
    public JipijigiInventoryResponse inventory(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return inventoryService.build();
    }

    @GetMapping("/boards")
    public List<JipijigiTargetBoardResponse> boards(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
        return boardService.all().stream().map(JipijigiMapper::toDto).toList();
    }

    @PostMapping("/boards")
    public JipijigiTargetBoardResponse saveBoard(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @Valid @RequestBody JipijigiTargetBoardRequest req) {
        return JipijigiMapper.toDto(boardService.save(req, principal.loginId()));
    }

    @PutMapping("/boards/{boardRowId}/enabled")
    public JipijigiTargetBoardResponse toggleBoard(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long boardRowId,
            @RequestParam boolean enabled) {
        return JipijigiMapper.toDto(boardService.setEnabled(boardRowId, enabled, principal.loginId()));
    }

    // ── commands ──────────────────────────────────────────────────────
    @PostMapping("/commands")
    public JipijigiCommandRunResponse runCommand(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @Valid @RequestBody JipijigiCommandRequest req) {
        JipijigiCommandService.RunResult result =
                commandService.run(req.getInstruction(), req.getCommandType(), principal.loginId());
        return JipijigiCommandRunResponse.builder()
                .command(JipijigiMapper.toDto(result.command()))
                .drafts(result.drafts().stream().map(JipijigiMapper::toDto).toList())
                .build();
    }

    @GetMapping("/commands")
    public Page<JipijigiCommandResponse> commands(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return commandService.history(page, size).map(JipijigiMapper::toDto);
    }

    // ── questions ─────────────────────────────────────────────────────
    @PostMapping("/questions")
    public List<JipijigiQuestionResponse> ingestQuestions(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestBody List<JipijigiQuestionRequest> requests) {
        return questionService.upsertAll(requests).stream().map(JipijigiMapper::toDto).toList();
    }

    @GetMapping("/questions")
    public List<JipijigiQuestionResponse> questions(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(defaultValue = "20") int limit) {
        return questionService.recent(limit).stream().map(JipijigiMapper::toDto).toList();
    }

    // ── drafts / review workflow ──────────────────────────────────────
    @GetMapping("/drafts")
    public List<JipijigiDraftResponse> drafts(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @RequestParam(required = false) String status) {
        List<JipijigiDraft> list = status == null || status.isBlank()
                ? workflowService.allDrafts()
                : workflowService.draftsByStatus(status);
        Map<Long, String> boardNames = boardNameMap();
        return list.stream()
                .map(d -> JipijigiMapper.toDto(d, boardNames.get(d.getTargetBoardId())))
                .toList();
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<JipijigiDraftDetailResponse> draftDetail(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId) {
        JipijigiDraft draft = workflowService.get(draftId);
        var traces = workflowService.tracesOf(draftId);
        return ResponseEntity.ok(JipijigiDraftDetailResponse.builder()
                .draft(JipijigiMapper.toDto(draft, boardNameMap().get(draft.getTargetBoardId())))
                .traces(JipijigiMapper.toTraceDtos(traces))
                .statusLogs(workflowService.statusLogsOf(draftId).stream().map(JipijigiMapper::toDto).toList())
                .hitCounts(traces.stream()
                        .filter(t -> !"GAP".equals(t.getRefId()))
                        .collect(Collectors.groupingBy(
                                t -> "STEP" + t.getStepNo() + ":" + t.getSourceClass(),
                                Collectors.counting())))
                .build());
    }

    @PutMapping("/drafts/{draftId}")
    public JipijigiDraftResponse editDraft(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId,
            @RequestBody JipijigiDraftEditRequest req) {
        JipijigiDraft draft = workflowService.edit(draftId, req.getTitle(), req.getBody(), principal.loginId());
        if (req.getTargetBoardId() != null) {
            draft = workflowService.retarget(draftId, req.getTargetBoardId(), principal.loginId());
        }
        return JipijigiMapper.toDto(draft, boardNameMap().get(draft.getTargetBoardId()));
    }

    @PostMapping("/drafts/{draftId}/review")
    public JipijigiDraftResponse submitReview(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId) {
        return JipijigiMapper.toDto(workflowService.submitReview(draftId, principal.loginId()));
    }

    @PostMapping("/drafts/{draftId}/approve")
    public JipijigiDraftResponse approve(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId,
            @RequestBody(required = false) JipijigiReviewActionRequest req) {
        Long boardId = req == null ? null : req.getTargetBoardId();
        JipijigiDraft draft = workflowService.approve(draftId, principal.loginId(), boardId);
        return JipijigiMapper.toDto(draft, boardNameMap().get(draft.getTargetBoardId()));
    }

    @PostMapping("/drafts/{draftId}/hold")
    public JipijigiDraftResponse hold(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId,
            @RequestBody(required = false) JipijigiReviewActionRequest req) {
        return JipijigiMapper.toDto(workflowService.hold(draftId, principal.loginId(),
                req == null ? null : req.getReason()));
    }

    @PostMapping("/drafts/{draftId}/reject")
    public JipijigiDraftResponse reject(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId,
            @RequestBody JipijigiReviewActionRequest req) {
        return JipijigiMapper.toDto(workflowService.reject(draftId, principal.loginId(), req.getReason()));
    }

    /** V1: 항상 409 — 게시는 운영자 수동 또는 후속 연동 구현 후. */
    @PostMapping("/drafts/{draftId}/publish")
    public ResponseEntity<JipijigiDraftResponse> publish(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
            @PathVariable Long draftId) {
        JipijigiDraft draft = workflowService.publish(draftId, principal.loginId());
        return ResponseEntity.ok(JipijigiMapper.toDto(draft));
    }

    // ── error mapping ─────────────────────────────────────────────────
    @ExceptionHandler(JipijigiWorkflowService.PublishUnavailableException.class)
    public ResponseEntity<Map<String, String>> handlePublishBlocked(
            JipijigiWorkflowService.PublishUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "PUBLISH_DISABLED",
                "message", e.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleRejected(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "AGENT_REQUEST_REJECTED",
                "message", e.getMessage() == null ? "요청이 거부되었습니다" : e.getMessage()));
    }

    private Map<Long, String> boardNameMap() {
        return boardService.all().stream()
                .filter(b -> b.getBoardRowId() != null)
                .collect(Collectors.toMap(JipijigiTargetBoard::getBoardRowId, JipijigiTargetBoard::getBoardName));
    }
}
