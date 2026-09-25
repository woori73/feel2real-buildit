package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft.DraftStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft.DraftType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraftStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiDraftStatusLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiResearchTraceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiTargetBoardRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지피지기 Agent 초안 워크플로 단위 테스트 (무DB).
 *
 * <p>강제 규칙:
 * <ul>
 *   <li>게시 대상은 allowlist의 enabled 게시판만 — 미지정/비활성 게시판으로는 승인 불가</li>
 *   <li>NAVER 실제 게시는 V1에서 항상 거부 (설정 플래그 + 미구현 클라이언트 이중 차단)</li>
 *   <li>모든 전이는 상태 로그에 기록, 초안 생성 시 STEP별 근거를 trace로 남긴다</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("지피지기 Agent 초안 승인/게시 차단 워크플로")
class JipijigiWorkflowServiceTest {

    @Mock
    private JipijigiDraftRepository draftRepository;
    @Mock
    private JipijigiDraftStatusLogRepository statusLogRepository;
    @Mock
    private JipijigiTargetBoardRepository targetBoardRepository;
    @Mock
    private JipijigiResearchTraceRepository traceRepository;
    @Mock
    private JipijigiAnswerSessionService answerSessions;

    private JipijigiWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new JipijigiWorkflowService(draftRepository, statusLogRepository,
                targetBoardRepository, traceRepository, answerSessions);
        ReflectionTestUtils.setField(service, "publishEnabled", false);
    }

    private JipijigiDraft draft(Long id, DraftStatus status) {
        return JipijigiDraft.builder()
                .draftId(id).draftType(DraftType.QUESTION_ANSWER)
                .title("제목").body("본문").status(status)
                .targetBoardId(7L).createdBy("jipijigi")
                .build();
    }

    private JipijigiTargetBoard board(Long id, boolean enabled, Long menuId) {
        return JipijigiTargetBoard.builder()
                .boardRowId(id).clubId(31312978L).menuId(menuId)
                .boardName("지피지기 agent").cafeName("새출발기금 부실차주 라이브러리")
                .enabled(enabled)
                .build();
    }

    @Test
    @DisplayName("allowlist enabled 게시판이 아니면 승인을 거부한다")
    void approveRequiresEnabledBoard() {
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.READY_FOR_REVIEW)));
        when(targetBoardRepository.findById(7L)).thenReturn(Optional.of(board(7L, false, 99L)));

        assertThatThrownBy(() -> service.approve(1L, "jipijigi", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowlist");
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("menuid 미확인(allowlist 밖 id) 게시판으로도 승인할 수 없다")
    void approveRejectsUnknownBoard() {
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.READY_FOR_REVIEW)));
        when(targetBoardRepository.findById(555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(1L, "jipijigi", 555L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("READY_FOR_REVIEW → APPROVED 승인 시 상태 로그가 남는다")
    void approveWritesStatusLog() {
        JipijigiDraft d = draft(1L, DraftStatus.READY_FOR_REVIEW);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(targetBoardRepository.findById(7L)).thenReturn(Optional.of(board(7L, true, 1L)));
        when(draftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        JipijigiDraft approved = service.approve(1L, "jipijigi", 7L);

        assertThat(approved.getStatus()).isEqualTo(DraftStatus.APPROVED);
        ArgumentCaptor<JipijigiDraftStatusLog> captor = ArgumentCaptor.forClass(JipijigiDraftStatusLog.class);
        verify(statusLogRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(captor.getValue().getToStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getActor()).isEqualTo("jipijigi");
    }

    @Test
    @DisplayName("publish-enabled=false면 게시는 항상 거부되고 상태를 유지한다 (V1: NAVER 게시 NO)")
    void publishBlockedByDefault() {
        JipijigiDraft d = draft(1L, DraftStatus.APPROVED);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.publish(1L, "jipijigi"))
                .isInstanceOf(JipijigiWorkflowService.PublishUnavailableException.class);
        assertThat(d.getStatus()).isEqualTo(DraftStatus.APPROVED);
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("플래그가 켜져도 menuid 미확인 게시판이면 게시하지 않는다")
    void publishBlockedWhenBoardNotVerified() {
        ReflectionTestUtils.setField(service, "publishEnabled", true);
        JipijigiDraft d = draft(1L, DraftStatus.APPROVED);
        d.setTargetBoardId(7L);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(targetBoardRepository.findById(7L)).thenReturn(Optional.of(board(7L, true, null)));

        assertThatThrownBy(() -> service.publish(1L, "jipijigi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clubid/menuid");
    }

    @Test
    @DisplayName("게시 클라이언트 미구현 — allowlist가 완비돼도 V1에서는 예외로 중단한다")
    void publishBlockedUntilClientImplemented() {
        ReflectionTestUtils.setField(service, "publishEnabled", true);
        JipijigiDraft d = draft(1L, DraftStatus.APPROVED);
        d.setTargetBoardId(7L);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(targetBoardRepository.findById(7L)).thenReturn(Optional.of(board(7L, true, 42L)));

        assertThatThrownBy(() -> service.publish(1L, "jipijigi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V1 범위 밖");
    }

    @Test
    @DisplayName("DRAFT가 아닌 초안은 검토 요청할 수 없다")
    void submitReviewOnlyFromDraft() {
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.APPROVED)));

        assertThatThrownBy(() -> service.submitReview(1L, "jipijigi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("반려에는 사유가 필수이고, 종결된 초안은 다시 반려할 수 없다")
    void rejectRequiresReasonAndTerminalGuard() {
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.DRAFT)));
        assertThatThrownBy(() -> service.reject(1L, "jipijigi", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사유");

        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.PUBLISHED)));
        assertThatThrownBy(() -> service.reject(1L, "jipijigi", "사유"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("APPROVED 초안 본문은 수정할 수 없다 (승인 후 임의 변경 방지)")
    void editBlockedAfterApproval() {
        when(draftRepository.findById(1L)).thenReturn(Optional.of(draft(1L, DraftStatus.APPROVED)));

        assertThatThrownBy(() -> service.edit(1L, "새 제목", "새 본문", "jipijigi"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("초안 생성 시 STEP별 근거 trace와 조사 공백 메모가 함께 저장된다")
    void createDraftPersistsTracesPerStep() {
        when(draftRepository.save(any())).thenAnswer(i -> {
            JipijigiDraft saved = i.getArgument(0);
            saved.setDraftId(5L);
            return saved;
        });
        ResearchPack pack = ResearchPack.builder()
                .question("질문")
                .internalHits(List.of(evidence(1, JipijigiResearchTrace.SourceClass.OFFICIAL_RULE)))
                .ccrsHits(List.of(evidence(2, JipijigiResearchTrace.SourceClass.CCRS_CAFE_CASE),
                        evidence(2, JipijigiResearchTrace.SourceClass.CCRS_CAFE_CASE)))
                .borrowerHits(List.of())
                .officialHits(List.of(evidence(4, JipijigiResearchTrace.SourceClass.OFFICIAL_RULE)))
                .extraAgencyHits(List.of())
                .gapNotes(List.of("STEP3 부실차주 카페: 키워드 일치 수집 사례 없음"))
                .build();
        JipijigiDraft draft = draft(null, null);
        draft.setQuestionId(11L);

        JipijigiDraft created = service.createDraft(draft, pack);

        ArgumentCaptor<JipijigiResearchTrace> captor = ArgumentCaptor.forClass(JipijigiResearchTrace.class);
        verify(traceRepository, times(5)).save(captor.capture());
        List<JipijigiResearchTrace> traces = captor.getAllValues();
        assertThat(traces).filteredOn(t -> t.getStepNo() == 1 && !"GAP".equals(t.getRefId())).hasSize(1);
        assertThat(traces).filteredOn(t -> t.getStepNo() == 2).hasSize(2);
        assertThat(traces).filteredOn(t -> t.getStepNo() == 4).hasSize(1);
        assertThat(traces).filteredOn(t -> "GAP".equals(t.getRefId()))
                .allSatisfy(t -> assertThat(t.getNote()).contains("STEP3"));
        assertThat(traces).allSatisfy(t -> assertThat(t.getDraftId()).isEqualTo(5L));
        assertThat(created.getStatus()).isEqualTo(DraftStatus.DRAFT);
        // 답변 생성 성공 즉시 축적 세션으로 저장(게시 여부와 무관)
        verify(answerSessions).recordFromDraft(created, pack);
    }

    @Test
    @DisplayName("패키지 없는 초안(소개글)은 답변 세션으로 저장하지 않는다")
    void introDraftIsNotAnAnswerSession() {
        when(draftRepository.save(any())).thenAnswer(i -> {
            JipijigiDraft saved = i.getArgument(0);
            saved.setDraftId(9L);
            return saved;
        });
        JipijigiDraft intro = draft(null, null);
        intro.setDraftType(DraftType.INTRO);

        service.createDraft(intro, null);

        verify(answerSessions, never()).recordFromDraft(any(), any());
        verify(traceRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 시 축적 세션을 ACCEPTED로 승격해 다음 질문 RAG에서 재사용 가능해진다")
    void approvePromotesAccumulatedSession() {
        JipijigiDraft d = draft(1L, DraftStatus.READY_FOR_REVIEW);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(targetBoardRepository.findById(7L)).thenReturn(Optional.of(board(7L, true, 1L)));
        when(draftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.approve(1L, "jipijigi", 7L);

        verify(answerSessions).markAccepted(1L);
    }

    @Test
    @DisplayName("반려 시 축적 세션은 REJECTED가 되어 재사용되지 않는다")
    void rejectDemotesAccumulatedSession() {
        JipijigiDraft d = draft(1L, DraftStatus.DRAFT);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(draftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reject(1L, "jipijigi", "근거 부족");

        verify(answerSessions).markRejected(1L);
    }

    @Test
    @DisplayName("운영자 수정은 축적 세션의 최종 답변에도 반영되고 수정 표시가 남는다")
    void editUpdatesAccumulatedAnswer() {
        JipijigiDraft d = draft(1L, DraftStatus.DRAFT);
        when(draftRepository.findById(1L)).thenReturn(Optional.of(d));
        when(draftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.edit(1L, "고친 제목", "고친 본문", "operator");

        verify(answerSessions).markEdited(1L, "고친 제목", "고친 본문");
    }

    private static Evidence evidence(int step, JipijigiResearchTrace.SourceClass cls) {
        return Evidence.builder()
                .stepNo(step).sourceClass(cls)
                .refType(JipijigiResearchTrace.RefType.KNOWLEDGE_ITEM)
                .refId("k-" + step).title("근거 " + step).excerpt("발췌")
                .build();
    }
}
