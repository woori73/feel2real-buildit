package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft.DraftStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraftStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiDraftStatusLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiResearchTraceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiTargetBoardRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 초안 생명주기: DRAFT → READY_FOR_REVIEW → APPROVED → PUBLISHED / REJECTED.
 *
 * <p>V1 안전 규칙(서버 강제):
 * <ul>
 *   <li>게시 대상은 newstart_jipijigi_target_boards allowlist 행만 허용 (질문게시판 등 임의 게시판 게시 불가)</li>
 *   <li>NAVER 실제 게시는 설정상 상시 비활성 — PUBLISHED 전이는 항상 거부</li>
 *   <li>모든 전이는 상태 로그에 provenance 기록</li>
 *   <li>답변(초안) 생성 성공 즉시 newstart_jipijigi_answer_sessions에 축적 저장, 운영자 승인은 ACCEPTED로 기록</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JipijigiWorkflowService {

    private final JipijigiDraftRepository draftRepository;
    private final JipijigiDraftStatusLogRepository statusLogRepository;
    private final JipijigiTargetBoardRepository targetBoardRepository;
    private final JipijigiResearchTraceRepository traceRepository;
    private final JipijigiAnswerSessionService answerSessions;

    /** V1 where true가 될 수 없다: NAVER 연동 구현·운영자 승인 전 게시 금지 더미. */
    @Value("${app.newstart.jipijigi.publish-enabled:false}")
    private boolean publishEnabled;

    public static class PublishUnavailableException extends RuntimeException {
        public PublishUnavailableException() {
            super("NAVER 게시 연동이 V1에서 비활성입니다. 승인은 가능하지만 실제 게시는 운영자 직접 게시 또는 후속 연동 구현 후 가능합니다.");
        }
    }

    @Transactional
    public JipijigiDraft createDraft(JipijigiDraft draft, ResearchPack pack) {
        draft.setStatus(DraftStatus.DRAFT);
        JipijigiDraft saved = draftRepository.save(draft);
        logTransition(saved.getDraftId(), null, DraftStatus.DRAFT.name(), saved.getCreatedBy(), "Agent 초안 생성");
        if (pack != null) {
            saveTraces(saved.getDraftId(), saved.getQuestionId(), pack);
            // 답변 생성 성공 = 게시 여부와 무관하게 즉시 축적 저장 (dedupe는 fingerprint가 담당)
            answerSessions.recordFromDraft(saved, pack);
        }
        return saved;
    }

    void saveTraces(Long draftId, Long questionId, ResearchPack pack) {
        recordStep(draftId, questionId, 1, pack.getInternalHits());
        recordStep(draftId, questionId, 2, pack.getCcrsHits());
        recordStep(draftId, questionId, 3, pack.getBorrowerHits());
        recordStep(draftId, questionId, 4, pack.getOfficialHits());
        recordStep(draftId, questionId, 5, pack.getExtraAgencyHits());
        for (String gap : pack.getGapNotes()) {
            traceRepository.save(JipijigiResearchTrace.builder()
                    .draftId(draftId).questionId(questionId)
                    .stepNo(JipijigiResearchTrace.STEP_INTERNAL)
                    .sourceClass(JipijigiResearchTrace.SourceClass.OTHER)
                    .refType(JipijigiResearchTrace.RefType.NONE)
                    .refId("GAP")
                    .note(gap)
                    .build());
        }
    }

    private void recordStep(Long draftId, Long questionId, int stepNo, List<Evidence> hits) {
        for (Evidence e : hits) {
            traceRepository.save(JipijigiResearchTrace.builder()
                    .draftId(draftId).questionId(questionId).stepNo(stepNo)
                    .sourceClass(e.getSourceClass())
                    .refType(e.getRefType())
                    .refId(e.getRefId())
                    .refUrl(e.getRefUrl())
                    .excerpt(e.getExcerpt())
                    .note(e.getNote())
                    .build());
        }
    }

    @Transactional
    public JipijigiDraft edit(Long draftId, String title, String body, String actor) {
        JipijigiDraft d = get(draftId);
        if (d.getStatus() != DraftStatus.DRAFT && d.getStatus() != DraftStatus.READY_FOR_REVIEW) {
            throw new IllegalStateException("수정 가능한 상태는 DRAFT/READY_FOR_REVIEW뿐입니다: " + d.getStatus());
        }
        if (title != null && !title.isBlank()) d.setTitle(title);
        if (body != null && !body.isBlank()) d.setBody(body);
        d.setUpdatedBy(actor);
        logTransition(d.getDraftId(), d.getStatus().name(), d.getStatus().name(), actor, "내용 수정");
        JipijigiDraft saved = draftRepository.save(d);
        answerSessions.markEdited(saved.getDraftId(), saved.getTitle(), saved.getBody());
        return saved;
    }

    @Transactional
    public JipijigiDraft submitReview(Long draftId, String actor) {
        JipijigiDraft d = require(draftId, DraftStatus.DRAFT, "READY_FOR_REVIEW");
        return move(d, DraftStatus.READY_FOR_REVIEW, actor, "검토 요청");
    }

    /** 보류: READY_FOR_REVIEW → DRAFT (운영자가 추가 조사를 지시하는 경로). */
    @Transactional
    public JipijigiDraft hold(Long draftId, String actor, String reason) {
        JipijigiDraft d = require(draftId, DraftStatus.READY_FOR_REVIEW, "DRAFT");
        return move(d, DraftStatus.DRAFT, actor, reason != null ? reason : "보류");
    }

    @Transactional
    public JipijigiDraft approve(Long draftId, String actor, Long targetBoardId) {
        JipijigiDraft d = require(draftId, DraftStatus.READY_FOR_REVIEW, "APPROVED");
        JipijigiTargetBoard board = targetBoardRepository.findById(
                targetBoardId != null ? targetBoardId : (d.getTargetBoardId() == null ? -1L : d.getTargetBoardId()))
                .filter(JipijigiTargetBoard::isEnabled)
                .orElseThrow(() -> new IllegalStateException(
                        "allowlist의 enabled 게시판만 승인/게시 대상으로 지정할 수 있습니다."));
        d.setTargetBoardId(board.getBoardRowId());
        JipijigiDraft approved = move(d, DraftStatus.APPROVED, actor, "승인 (대상 게시판: " + board.getBoardName() + ")");
        // 운영자 검토 통과 → 이 시점부터 다음 질문 STEP1 RAG에서 재사용 가능하다.
        answerSessions.markAccepted(approved.getDraftId());
        return approved;
    }

    @Transactional
    public JipijigiDraft reject(Long draftId, String actor, String reason) {
        JipijigiDraft d = get(draftId);
        if (d.getStatus() == DraftStatus.PUBLISHED || d.getStatus() == DraftStatus.REJECTED) {
            throw new IllegalStateException("이미 종결된 초안입니다: " + d.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유 필수");
        }
        d.setRejectReason(reason);
        JipijigiDraft rejected = move(d, DraftStatus.REJECTED, actor, reason);
        // 반려된 답변은 축적 DB에 남아도 다음 질문 RAG에서 검색되지 않는다.
        answerSessions.markRejected(rejected.getDraftId());
        return rejected;
    }

    /** V1에서 항상 거부된다. 상태는 APPROVED에 유지된다. */
    @Transactional
    public JipijigiDraft publish(Long draftId, String actor) {
        JipijigiDraft d = require(draftId, DraftStatus.APPROVED, "PUBLISHED");
        if (!publishEnabled) {
            throw new PublishUnavailableException();
        }
        JipijigiTargetBoard board = targetBoardRepository.findById(d.getTargetBoardId())
                .filter(JipijigiTargetBoard::isPostable)
                .orElseThrow(() -> new IllegalStateException(
                        "게시 대상 게시판의 clubid/menuid가 allowlist에서 확인되지 않았습니다. 임의 게시판 게시 금지."));
        // NAVER 공식 글쓰기 연동(POST /v1/cafe/{clubid}/menu/{menuid}/articles)은 V1 범위 밖.
        throw new IllegalStateException("NAVER 게시 클라이언트가 아직 구현되지 않았습니다 (V1 범위 밖).");
    }

    @Transactional(readOnly = true)
    public List<JipijigiDraft> allDrafts() {
        return draftRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<JipijigiDraft> draftsByStatus(String status) {
        DraftStatus parsed;
        try {
            parsed = DraftStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 초안 상태: " + status);
        }
        return draftRepository.findByStatusOrderByUpdatedAtDesc(parsed);
    }

    /** 상태 변경 없이 게시 대상 게시판만 allowlist 행으로 재지정. */
    @Transactional
    public JipijigiDraft retarget(Long draftId, Long targetBoardId, String actor) {
        JipijigiDraft d = get(draftId);
        JipijigiTargetBoard board = targetBoardRepository.findById(targetBoardId)
                .filter(JipijigiTargetBoard::isEnabled)
                .orElseThrow(() -> new IllegalStateException("allowlist의 enabled 게시판만 대상으로 지정할 수 있습니다."));
        d.setTargetBoardId(board.getBoardRowId());
        d.setUpdatedBy(actor);
        return draftRepository.save(d);
    }

    @Transactional(readOnly = true)
    public List<JipijigiResearchTrace> tracesOf(Long draftId) {
        return traceRepository.findByDraftIdOrderByStepNoAscTraceIdAsc(draftId);
    }

    @Transactional(readOnly = true)
    public List<JipijigiDraftStatusLog> statusLogsOf(Long draftId) {
        return statusLogRepository.findByDraftIdOrderByCreatedAtAsc(draftId);
    }

    @Transactional(readOnly = true)
    public JipijigiDraft get(Long draftId) {
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new IllegalArgumentException("초안 없음: " + draftId));
    }

    private JipijigiDraft require(Long draftId, DraftStatus from, String toLabel) {
        JipijigiDraft d = get(draftId);
        if (d.getStatus() != from) {
            throw new IllegalStateException(from + " 상태에서만 " + toLabel + "(으)로 전환할 수 있습니다. 현재: " + d.getStatus());
        }
        return d;
    }

    private JipijigiDraft move(JipijigiDraft d, DraftStatus to, String actor, String reason) {
        String from = d.getStatus().name();
        d.setStatus(to);
        d.setUpdatedBy(actor);
        JipijigiDraft saved = draftRepository.save(d);
        logTransition(saved.getDraftId(), from, to.name(), actor, reason);
        return saved;
    }

    private void logTransition(Long draftId, String from, String to, String actor, String reason) {
        statusLogRepository.save(JipijigiDraftStatusLog.builder()
                .draftId(draftId).fromStatus(from).toStatus(to).actor(actor).reason(reason)
                .build());
    }
}
