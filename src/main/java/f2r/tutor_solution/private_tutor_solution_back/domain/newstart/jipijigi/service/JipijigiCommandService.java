package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiCommandRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 수동 명령 실행 (V1: 화면 버튼/자유 명령 1회 = 1실행, 자동 스케줄 없음).
 *
 * <p>[최근 질문 5개 조사] [신복위 카페 사례 조사] [Agent 소개글 작성] [직접 명령 입력]
 * 각 실행은 강제 조사 사다리(STEP1..5)를 통과한 초안만 만들며, 게시는 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JipijigiCommandService {

    public static final int RECENT_QUESTION_LIMIT = 5;

    private final JipijigiCommandRepository commandRepository;
    private final JipijigiQuestionService questionService;
    private final JipijigiResearchService researchService;
    private final JipijigiDraftComposer composer;
    private final JipijigiWorkflowService workflowService;
    private final JipijigiIntroContent introContent;
    private final JipijigiBoardService boardService;

    /** 실행 결과: 명령행 + 이 명령이 만든 초안. */
    public record RunResult(JipijigiCommand command, List<JipijigiDraft> drafts) {
    }

    @Transactional(readOnly = true)
    public Page<JipijigiCommand> history(int page, int size) {
        return commandRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    public RunResult run(String instruction, String commandType, String actor) {
        CommandType type = resolve(commandType, instruction);
        JipijigiCommand command = commandRepository.save(JipijigiCommand.builder()
                .instruction(instruction == null ? type.name() : instruction)
                .commandType(type)
                .status(CommandStatus.RECEIVED)
                .createdBy(actor)
                .build());

        command.setStatus(CommandStatus.RUNNING);
        commandRepository.save(command);

        List<JipijigiDraft> drafts = new ArrayList<>();
        try {
            switch (type) {
                case INTRO_POST -> drafts.add(createIntroDraft(command, actor));
                case RECENT_QUESTIONS -> drafts.addAll(answerRecentQuestions(command, actor));
                case CCRS_RESEARCH -> drafts.add(createResearchReport(command, instruction, actor));
                case CUSTOM -> drafts.add(createResearchReport(command, instruction, actor));
            }
            command.setResultSummary(summary(type, drafts));
            command.setStatus(CommandStatus.DONE);
        } catch (RuntimeException e) {
            log.warn("jipijigi command failed type={} id={}", type, command.getCommandId(), e);
            command.setStatus(CommandStatus.FAILED);
            command.setErrorMessage(truncate(e.getMessage()));
        }
        commandRepository.save(command);
        return new RunResult(command, drafts);
    }

    /** 버튼/문장 → 프리셋 판정. 명시 commandType이 있으면 그것을 따른다. */
    static CommandType resolve(String explicit, String instruction) {
        if (explicit != null && !explicit.isBlank()) {
            return CommandType.valueOf(explicit.trim().toUpperCase());
        }
        String text = instruction == null ? "" : instruction.toLowerCase();
        if (text.contains("소개")) return CommandType.INTRO_POST;
        if (text.contains("신복위") || text.contains(" 신용회복")) return CommandType.CCRS_RESEARCH;
        if (text.contains("최근 질문") || text.contains("질문 5") || text.contains("질문5")) return CommandType.RECENT_QUESTIONS;
        return CommandType.CUSTOM;
    }

    private List<JipijigiDraft> answerRecentQuestions(JipijigiCommand command, String actor) {
        List<JipijigiQuestion> questions = questionService.recent(RECENT_QUESTION_LIMIT);
        List<JipijigiDraft> out = new ArrayList<>();
        for (JipijigiQuestion q : questions) {
            ResearchPack pack = researchService.research(q.questionText());
            JipijigiDraft draft = JipijigiDraft.builder()
                    .commandId(command.getCommandId())
                    .questionId(q.getQuestionId())
                    .draftType(JipijigiDraft.DraftType.QUESTION_ANSWER)
                    .title(composer.composeAnswerTitle(q))
                    .body(composer.composeAnswerBody(pack, q))
                    .createdBy(actor)
                    .build();
            boardService.enabledAgentBoard().ifPresent(b -> draft.setTargetBoardId(b.getBoardRowId()));
            out.add(workflowService.createDraft(draft, pack));
        }
        if (questions.isEmpty()) {
            // 질문을 지어내지 않는다 — 수집이 안 되었다는 사실만 남긴다.
            out.add(createResearchReport(command,
                    "최근 질문 " + RECENT_QUESTION_LIMIT + "개 조사를 시도했지만 newstart_jipijigi_questions에 수집된 질문이 0건입니다. "
                            + "질문게시판(clubid 31312978/menuid 1) 실수집 후 다시 명령하세요.", actor));
        } else if (questions.size() < RECENT_QUESTION_LIMIT) {
            out.add(createResearchReport(command,
                    "수집된 질문이 " + questions.size() + "건뿐이어서 " + RECENT_QUESTION_LIMIT + "건 중 나머지는 조사하지 못했습니다.", actor));
        }
        return out;
    }

    private JipijigiDraft createIntroDraft(JipijigiCommand command, String actor) {
        JipijigiDraft draft = JipijigiDraft.builder()
                .commandId(command.getCommandId())
                .draftType(JipijigiDraft.DraftType.INTRO)
                .title(introContent.title())
                .body(introContent.body())
                .createdBy(actor)
                .build();
        boardService.enabledAgentBoard().ifPresent(b -> draft.setTargetBoardId(b.getBoardRowId()));
        return workflowService.createDraft(draft, null);
    }

    private JipijigiDraft createResearchReport(JipijigiCommand command, String instruction, String actor) {
        String topic = instruction == null || instruction.isBlank() ? "신복위 카페 사례" : instruction;
        ResearchPack pack = researchService.research(topic);
        JipijigiDraft draft = JipijigiDraft.builder()
                .commandId(command.getCommandId())
                .draftType(JipijigiDraft.DraftType.RESEARCH_REPORT)
                .title("지피지기 Agent 조사 기록 — " + shorten(topic))
                .body(composer.composeAnswerBody(pack, null))
                .createdBy(actor)
                .build();
        boardService.enabledAgentBoard().ifPresent(b -> draft.setTargetBoardId(b.getBoardRowId()));
        return workflowService.createDraft(draft, pack);
    }

    private static String summary(CommandType type, List<JipijigiDraft> drafts) {
        return type + " 실행: 초안 " + drafts.size() + "건 생성 (상태 DRAFT, 게시 미수행)";
    }

    private static String shorten(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 60 ? t : t.substring(0, 60) + "…";
    }

    private static String truncate(String s) {
        if (s == null) return "unknown error";
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
