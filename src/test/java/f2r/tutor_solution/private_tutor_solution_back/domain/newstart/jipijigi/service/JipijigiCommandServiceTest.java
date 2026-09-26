package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiCommand.CommandType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiCommandRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수동 명령 4종(최근 질문/신복위/소개글/직접) 실행 검증.
 * 핵심: 자료 부족을 지어로 덮지 않는다 — 질문이 없으면 "0건"이라는 사실만 초안에 남긴다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("지피지기 Agent 수동 명령")
class JipijigiCommandServiceTest {

    private static final long AGENT_BOARD_ID = 7L;

    @Mock
    private JipijigiCommandRepository commandRepository;
    @Mock
    private JipijigiQuestionService questionService;
    @Mock
    private JipijigiResearchService researchService;
    @Mock
    private JipijigiWorkflowService workflowService;
    @Mock
    private JipijigiBoardService boardService;

    private JipijigiCommandService service;

    @BeforeEach
    void setUp() {
        service = new JipijigiCommandService(commandRepository, questionService, researchService,
                new JipijigiDraftComposer(), workflowService, new JipijigiIntroContent(), boardService);
        lenient().when(commandRepository.save(any())).thenAnswer(i -> {
            JipijigiCommand c = i.getArgument(0);
            if (c.getCommandId() == null) c.setCommandId(100L);
            return c;
        });
        lenient().when(workflowService.createDraft(any(), any())).thenAnswer(i -> {
            JipijigiDraft d = i.getArgument(0);
            if (d.getDraftId() == null) d.setDraftId(500L);
            return d;
        });
        lenient().when(boardService.enabledAgentBoard()).thenReturn(Optional.of(JipijigiTargetBoard.builder()
                .boardRowId(AGENT_BOARD_ID).clubId(31312978L).boardName("지피지기 agent").enabled(true).build()));
    }

    private void researchEchoesQuestion() {
        lenient().when(researchService.research(anyString()))
                .thenAnswer(i -> pack(i.getArgument(0)));
    }

    private static ResearchPack pack(String question) {
        return ResearchPack.builder().question(question)
                .internalHits(List.of()).ccrsHits(List.of()).borrowerHits(List.of())
                .officialHits(List.of()).extraAgencyHits(List.of())
                .gapNotes(List.of("STEP1 내부 ACTIVE 지식 근거 0건(기준 3건 미만) → 외부 사례조사 단계 필수")).build();
    }

    @ParameterizedTest
    @DisplayName("버튼 문장에서 프리셋을 판정하고, 그 외 문장은 CUSTOM으로 남긴다")
    @CsvSource({
            "'최근 질문 5개 조사', RECENT_QUESTIONS",
            "'신복위 카페 사례 조사', CCRS_RESEARCH",
            "'Agent 소개글 작성', INTRO_POST",
            "'감면율 산정 기준이 궁금해요', CUSTOM"
    })
    void resolvePresets(String instruction, CommandType expected) {
        assertThat(JipijigiCommandService.resolve(null, instruction)).isEqualTo(expected);
    }

    @Test
    @DisplayName("명시 commandType이 문장 판정보다 우선한다")
    void explicitTypeWins() {
        assertThat(JipijigiCommandService.resolve("INTRO_POST", "최근 질문 5개 조사")).isEqualTo(CommandType.INTRO_POST);
    }

    @Test
    @DisplayName("소개글 명령은 AI 고지를 포함한 INTRO 초안을 만들고 게시하지 않는다")
    void introCommandCreatesDraft() {
        JipijigiCommandService.RunResult result = service.run("Agent 소개글 작성", "INTRO_POST", "jipijigi");

        assertThat(result.command().getStatus()).isEqualTo(CommandStatus.DONE);
        assertThat(result.drafts()).singleElement().satisfies(d -> {
            assertThat(d.getDraftType()).isEqualTo(JipijigiDraft.DraftType.INTRO);
            assertThat(d.getBody()).contains(JipijigiDraftComposer.AI_DISCLAIMER).contains("개발이 진행 중");
            assertThat(d.getTargetBoardId()).isEqualTo(AGENT_BOARD_ID);
        });
        assertThat(result.command().getResultSummary()).contains("게시 미수행");
    }

    @Test
    @DisplayName("수집 질문이 0건이면 질문을 지어내지 않고 '0건' 사실만 남긴다")
    void noInventedQuestions() {
        researchEchoesQuestion();
        when(questionService.recent(anyInt())).thenReturn(List.of());

        JipijigiCommandService.RunResult result = service.run("최근 질문 5개 조사", "RECENT_QUESTIONS", "jipijigi");

        assertThat(result.drafts()).singleElement().satisfies(d -> {
            assertThat(d.getDraftType()).isEqualTo(JipijigiDraft.DraftType.RESEARCH_REPORT);
            assertThat(d.getBody()).contains("0건");
        });
    }

    @Test
    @DisplayName("질문이 3건뿐이면 5건을 다 조사하지 못했다고 남긴다")
    void partialCollectionIsDeclared() {
        researchEchoesQuestion();
        List<JipijigiQuestion> three = List.of(
                question(1L, "111"), question(2L, "222"), question(3L, "333"));
        when(questionService.recent(anyInt())).thenReturn(three);

        JipijigiCommandService.RunResult result = service.run("최근 질문 5개 조사", "RECENT_QUESTIONS", "jipijigi");

        assertThat(result.drafts()).hasSize(4); // 답변 3건 + 부족 사유 보고서 1건
        assertThat(result.drafts().get(3).getBody()).contains("3건");
    }

    @Test
    @DisplayName("UNREADABLE 질문은 표시를 유지한 채 제목 기준으로 초안을 만든다")
    void unreadableQuestionKeepsMarker() {
        JipijigiQuestion q = question(9L, "6010");
        researchEchoesQuestion();
        when(questionService.recent(eq(JipijigiCommandService.RECENT_QUESTION_LIMIT))).thenReturn(List.of(q));

        JipijigiCommandService.RunResult result = service.run("최근 질문 5개 조사", "RECENT_QUESTIONS", "jipijigi");

        // 질문 1건의 답변 초안 + 5건에 못 미친다는 부족 보고서 1건
        assertThat(result.drafts()).hasSize(2);
        JipijigiDraft answer = result.drafts().get(0);
        assertThat(answer.getQuestionId()).isEqualTo(9L);
        assertThat(answer.getDraftType()).isEqualTo(JipijigiDraft.DraftType.QUESTION_ANSWER);
        assertThat(answer.getTitle()).startsWith("지피지기 Agent가 답합니다");
        assertThat(answer.getBody()).contains("UNREADABLE");
        verify(workflowService, times(2)).createDraft(any(), any());
    }

    @Test
    @DisplayName("조사 중 예외는 명령을 FAILED로 남긴다 (조용히 성공 처리 금지)")
    void failureIsRecorded() {
        when(questionService.recent(anyInt())).thenThrow(new IllegalStateException("DB 접속 실패"));

        JipijigiCommandService.RunResult result = service.run("최근 질문 5개 조사", "RECENT_QUESTIONS", "jipijigi");

        assertThat(result.command().getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(result.command().getErrorMessage()).contains("DB 접속 실패");
        assertThat(result.drafts()).isEmpty();
        verify(workflowService, never()).createDraft(any(), any());
    }

    private static JipijigiQuestion question(Long id, String articleId) {
        return JipijigiQuestion.builder()
                .questionId(id).articleId(articleId).clubId(31312978L).menuId(1L)
                .title("부실차주 판정 기준이 궁금합니다").body("").bodyStatus(JipijigiQuestion.BodyStatus.UNREADABLE)
                .build();
    }
}
