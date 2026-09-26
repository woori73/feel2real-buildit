package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.PublishedAs;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiAnswerSession.ReviewState;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft.DraftStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiDraft.DraftType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiTargetBoard;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiAnswerSessionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiQuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.repository.JipijigiTargetBoardRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1005 답변 세션 자동 축적 — 실제 DB(H2 MySQL 모드)에서 요청 흐름을 검증한다.
 *
 * <p>질문 1건 실행 → 답변 생성 → DB 자동저장 → 동일/유사 질문 재실행 → 저장된 답변·근거가 RAG에서 검색되는지.
 * 부수 규칙: dedupe, 승인 전 RAG 제외, 게시 여부와 무관한 저장, 출처 분류 분리.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({JpaConfig.class, JipijigiAnswerSessionService.class, JipijigiWorkflowService.class, JipijigiResearchService.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:jipijigi_v1000;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("지피지기 Agent 답변 세션 자동 축적/RAG 재사용 (V1005 DB)")
class JipijigiAnswerSessionDbTest {

    private static final String QUESTION = "자동차 담보가 있으면 새출발기금 감면을 못 받나요";

    @Autowired JipijigiWorkflowService workflowService;
    @Autowired JipijigiResearchService researchService;
    @Autowired JipijigiAnswerSessionService answerSessions;
    @Autowired JipijigiAnswerSessionRepository sessionRepository;
    @Autowired JipijigiQuestionRepository questionRepository;
    @Autowired JipijigiTargetBoardRepository targetBoardRepository;

    private JipijigiQuestion seedQuestion() {
        return questionRepository.save(JipijigiQuestion.builder()
                .articleId("6024").clubId(31312978L).menuId(1L).boardName("질문게시판")
                .title("자동차 담보 질문").url("https://cafe.naver.com/newhopero/6024")
                .writtenAt(LocalDateTime.now().minusDays(1)).body(QUESTION)
                .bodyStatus(JipijigiQuestion.BodyStatus.READABLE).commentCount(0)
                .collectedAt(LocalDateTime.now())
                .build());
    }

    private JipijigiTargetBoard seedBoard() {
        return targetBoardRepository.save(JipijigiTargetBoard.builder()
                .clubId(31312978L).menuId(23L).boardName("지피지기 agent")
                .cafeName("새출발기금 부실차주 라이브러리").enabled(true).createdBy("operator")
                .build());
    }

    private JipijigiDraft answerDraft(Long questionId) {
        return JipijigiDraft.builder()
                .questionId(questionId).draftType(DraftType.QUESTION_ANSWER)
                .title("[지피지기 Agent] 자동차 담보 답변").body("담보가 있어도 신청 가능합니다. 유효담보가액 초과분만 감면 대상입니다.")
                .createdBy("jipijigi")
                .build();
    }

    /** 내부 1건 + 외부 사례 1건 — STEP1 부족이라 외부 카페 검색을 수행한 상태. */
    private ResearchPack packFor(String question) {
        Evidence internal = Evidence.builder().stepNo(1).sourceClass(SourceClass.OPERATOR_MATERIAL)
                .refType(RefType.KNOWLEDGE_ITEM).refId("k-1").title("운영자 전자책 9장").excerpt("차량 담보 규칙")
                .build();
        Evidence borrower = Evidence.builder().stepNo(3).sourceClass(SourceClass.BORROWER_CAFE_CASE)
                .refType(RefType.CAFE_ARTICLE).refId("149").refUrl("https://cafe.naver.com/newhopero/149")
                .title("부실차주 카페 사례 149").excerpt("자동차 담보 인정액 관련 후기")
                .build();
        return ResearchPack.builder().question(question)
                .internalHits(List.of(internal)).ccrsHits(List.of()).borrowerHits(List.of(borrower))
                .officialHits(List.of()).extraAgencyHits(List.of()).gapNotes(List.of("STEP4 공식 원문 미확인"))
                .externalSearched(true).build();
    }

    @Test
    @DisplayName("답변 생성 성공 즉시 세션이 자동 저장되고 게시 여부와 무관하게 CANDIDATE로 남는다")
    void answerIsAutoSavedOnDraftCreation() {
        JipijigiQuestion question = seedQuestion();
        ResearchPack pack = packFor(question.questionText());

        JipijigiDraft draft = workflowService.createDraft(answerDraft(question.getQuestionId()), pack);

        JipijigiAnswerSession session = sessionRepository.findByDraftId(draft.getDraftId()).orElseThrow();
        assertThat(session.getQuestionText()).isEqualTo(question.questionText());
        assertThat(session.getAnswerBody()).isEqualTo(draft.getBody());
        assertThat(session.getAnsweredAt()).isNotNull();
        assertThat(session.getQuestionType()).isEqualTo("QUESTION_ANSWER");
        assertThat(session.getQuestionTopic()).isEqualTo("담보채무");
        assertThat(session.getSourceClubId()).isEqualTo(31312978L);
        assertThat(session.getSourceArticleId()).isEqualTo("6024");
        assertThat(session.getEvidenceInternal()).contains("k-1").contains("OPERATOR_MATERIAL");
        assertThat(session.getEvidenceBorrower()).contains("newhopero/149").contains("BORROWER_CAFE_CASE");
        assertThat(session.getEvidenceCcrs()).isEqualTo("[]");
        assertThat(session.getDbHits()).contains("\"step3_borrower\":1").contains("\"total\":2");
        assertThat(session.isExternalSearched()).isTrue();
        assertThat(session.getConfidence().name()).isEqualTo("MEDIUM");
        assertThat(session.isOperatorEdited()).isFalse();
        assertThat(session.getPublishedAs()).isEqualTo(PublishedAs.NONE);
        assertThat(session.getReviewState()).isEqualTo(ReviewState.CANDIDATE);
        // 게시(V1 차단) 없이도 저장이 완료되었다 = 게시 성공 여부와 무관
        assertThat(draft.getPublishedUrl()).isNull();
    }

    @Test
    @DisplayName("동일 질문+동일 근거 재실행은 중복 저장하지 않고, 근거가 다르면 새 세션이 된다")
    void dedupesSameQuestionAndEvidence() {
        JipijigiQuestion question = seedQuestion();
        ResearchPack pack = packFor(question.questionText());

        workflowService.createDraft(answerDraft(question.getQuestionId()), pack);
        JipijigiDraft second = workflowService.createDraft(answerDraft(question.getQuestionId()), pack);

        List<JipijigiAnswerSession> all = sessionRepository.findByQuestionIdOrderByAnsweredAtDesc(question.getQuestionId());
        assertThat(all).hasSize(1);
        assertThat(sessionRepository.findByDraftId(second.getDraftId())).isEmpty();

        // 같은 질문이지만 근거 세트가 달라지면(추가 조사) 새 세션으로 축적된다
        ResearchPack richer = ResearchPack.builder().question(question.questionText())
                .internalHits(pack.getInternalHits())
                .ccrsHits(List.of(Evidence.builder().stepNo(2).sourceClass(SourceClass.CCRS_CAFE_CASE)
                        .refType(RefType.CAFE_ARTICLE).refId("ccrs-77").refUrl("https://m.cafe.naver.com/ccrs/77")
                        .title("신복위 카페 사례").excerpt("감면 상담 후기").build()))
                .borrowerHits(pack.getBorrowerHits()).officialHits(List.of()).extraAgencyHits(List.of())
                .gapNotes(List.of()).externalSearched(true).build();
        workflowService.createDraft(answerDraft(question.getQuestionId()), richer);

        assertThat(sessionRepository.findByQuestionIdOrderByAnsweredAtDesc(question.getQuestionId())).hasSize(2);
        assertThat(sessionRepository.findAll().stream()
                .filter(s -> s.getFingerprint().length() == 64).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("승인 전 축적 답변은 RAG에 나오지 않고, 운영자 승인 후 STEP1에서 재검색된다")
    void acceptedSessionIsReusedByRagBeforeExternalSearch() {
        JipijigiQuestion question = seedQuestion();
        JipijigiDraft draft = workflowService.createDraft(answerDraft(question.getQuestionId()), packFor(question.questionText()));

        // CANDIDATE: 아직 운영자 검토 전 → 재사용 대상 아님
        assertThat(researchService.research(QUESTION).getInternalHits())
                .noneSatisfy(e -> assertThat(e.getRefType()).isEqualTo(RefType.ANSWER_SESSION));

        workflowService.submitReview(draft.getDraftId(), "operator");
        workflowService.approve(draft.getDraftId(), "operator", seedBoard().getBoardRowId());
        assertThat(sessionRepository.findByDraftId(draft.getDraftId()).orElseThrow().getReviewState())
                .isEqualTo(ReviewState.ACCEPTED);

        ResearchPack again = researchService.research(QUESTION);
        assertThat(again.getInternalHits()).anySatisfy(e -> {
            assertThat(e.getRefType()).isEqualTo(RefType.ANSWER_SESSION);
            // Agent 답변은 절대 공식 규정으로 승격되지 않는다
            assertThat(e.getSourceClass()).isEqualTo(SourceClass.INTERNAL_CASE);
            assertThat(e.getExcerpt()).contains("유효담보가액");
        });
        // 축적 답변이 STEP1(외부 카페 검색 이전)에서 잡혔다
        assertThat(again.getInternalHits().indexOf(again.getInternalHits().stream()
                .filter(e -> e.getRefType() == RefType.ANSWER_SESSION).findFirst().orElseThrow()))
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("내부 근거(축적 답변 포함)가 충분하면 외부 카페 검색은 생략되고 사유가 남는다")
    void sufficientInternalAccumulationSkipsExternalCafe() {
        JipijigiQuestion question = seedQuestion();
        JipijigiTargetBoard board = seedBoard();
        for (int i = 0; i < 3; i++) {
            ResearchPack pack = packFor("자동차 담보 인정액 질문 " + i + " — 감면율과 재산 산정");
            JipijigiDraft d = workflowService.createDraft(answerDraft(question.getQuestionId()), pack);
            workflowService.submitReview(d.getDraftId(), "operator");
            workflowService.approve(d.getDraftId(), "operator", board.getBoardRowId());
        }

        ResearchPack result = researchService.research("자동차 담보 인정액 질문 — 감면율과 재산 산정");

        assertThat(result.getInternalHits()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.isExternalSearched()).isFalse();
        assertThat(result.getCcrsHits()).isEmpty();
        assertThat(result.getBorrowerHits()).isEmpty();
        assertThat(result.getGapNotes()).anySatisfy(g -> assertThat(g).contains("생략 사유").contains("내부 근거 충분"));
    }

    @Test
    @DisplayName("운영자 수정은 최종 답변에 반영되고, 실제 게시/댓글 보고는 세션에 따로 기록된다")
    void operatorEditAndExposureReportAreRecorded() {
        JipijigiQuestion question = seedQuestion();
        JipijigiDraft draft = workflowService.createDraft(answerDraft(question.getQuestionId()), packFor(question.questionText()));

        workflowService.edit(draft.getDraftId(), null, "정정: 자동차는 홈택스 조회 시세 4,000만원 이하는 재산가액 0원으로 봅니다.", "operator");
        answerSessions.reportExposure(draft.getDraftId(), PublishedAs.COMMENT,
                "https://cafe.naver.com/newhopero/6024", "57142585", null);

        JipijigiAnswerSession session = sessionRepository.findByDraftId(draft.getDraftId()).orElseThrow();
        assertThat(session.isOperatorEdited()).isTrue();
        assertThat(session.getAnswerBody()).contains("홈택스");
        assertThat(session.getPublishedAs()).isEqualTo(PublishedAs.COMMENT);
        assertThat(session.getSourceCommentId()).isEqualTo("57142585");
        assertThat(session.getPublishedUrl()).contains("newhopero/6024");

        // 후속 회원 댓글·실제 결과 연결
        answerSessions.attachFollowUp(session.getSessionId(), "comment-57142999");
        assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getFollowUpRef())
                .isEqualTo("comment-57142999");
    }

    @Test
    @DisplayName("소개글은 답변 세션으로 저장하지 않고, 반려된 답변은 RAG에서 검색되지 않는다")
    void introSkippedAndRejectedNotReused() {
        JipijigiQuestion question = seedQuestion();
        workflowService.createDraft(JipijigiDraft.builder()
                .draftType(DraftType.INTRO).title("지피지기 Agent 소개").body("안녕하세요")
                .createdBy("jipijigi").build(), null);
        assertThat(sessionRepository.count()).isZero();

        JipijigiDraft draft = workflowService.createDraft(answerDraft(question.getQuestionId()), packFor(question.questionText()));
        workflowService.submitReview(draft.getDraftId(), "operator");
        workflowService.approve(draft.getDraftId(), "operator", seedBoard().getBoardRowId());
        workflowService.reject(draft.getDraftId(), "operator", "출처가 약해 보류");

        assertThat(sessionRepository.findByDraftId(draft.getDraftId()).orElseThrow().getReviewState())
                .isEqualTo(ReviewState.REJECTED);
        assertThat(researchService.research(QUESTION).getInternalHits())
                .noneSatisfy(e -> assertThat(e.getRefType()).isEqualTo(RefType.ANSWER_SESSION));
    }
}
