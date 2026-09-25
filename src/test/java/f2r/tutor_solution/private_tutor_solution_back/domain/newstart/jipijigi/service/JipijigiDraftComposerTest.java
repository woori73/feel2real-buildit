package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiQuestion;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.RefType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity.JipijigiResearchTrace.SourceClass;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 원문 템플릿 검증 — §8 고정 순서와 안전 문구(AI 고지, 예측 금지)가 빠지지 않는지 본다.
 */
@DisplayName("지피지기 Agent 답변 템플릿")
class JipijigiDraftComposerTest {

    private final JipijigiDraftComposer composer = new JipijigiDraftComposer();

    private JipijigiQuestion question(JipijigiQuestion.BodyStatus status, String body) {
        return JipijigiQuestion.builder()
                .questionId(1L).articleId("5999").clubId(31312978L).menuId(1L)
                .title("새출발기금 협약기관 조회가 안 됩니다")
                .url("https://cafe.naver.com/31312978/5999")
                .body(body).bodyStatus(status)
                .build();
    }

    private ResearchPack pack(List<Evidence> internal, List<Evidence> borrower, List<String> gaps) {
        return ResearchPack.builder()
                .question("새출발기금 협약기관 조회")
                .internalHits(internal)
                .ccrsHits(List.of())
                .borrowerHits(borrower)
                .officialHits(List.of(evidence(SourceClass.OFFICIAL_RULE, RefType.OFFICIAL_UPDATE,
                        "https://www.newstartfund.or.kr/x", "협약기관 목록")))
                .extraAgencyHits(List.of())
                .gapNotes(gaps)
                .build();
    }

    private Evidence evidence(SourceClass cls, RefType type, String url, String title) {
        return Evidence.builder().stepNo(1).sourceClass(cls).refType(type)
                .refId("1").refUrl(url).title(title).excerpt("저장된 발췌").build();
    }

    @Test
    @DisplayName("9개 섹션이 고정 순서로 나오고 AI 고지/예측 금지 문구가 포함된다")
    void templateSectionsInFixedOrder() {
        String body = composer.composeAnswerBody(
                pack(List.of(evidence(SourceClass.INTERNAL_CASE, RefType.KNOWLEDGE_ITEM, null, "내부 지식")),
                        List.of(evidence(SourceClass.BORROWER_CAFE_CASE, RefType.EMPIRICAL_CASE,
                                "https://cafe.naver.com/31312978/5740", "사례 글")),
                        List.of("STEP2 신복위: 저장된 근거 없음")),
                question(JipijigiQuestion.BodyStatus.READABLE, "협약기관이 안 나와요"));

        String[] headers = {"[질문 요약]", "[지피지기 Agent 답변]", "[공식 기준]", "[우리 DB에서 찾은 관련 자료]",
                "[비슷한 사례]", "[사례와 현재 질문의 차이]", "[추가로 확인할 사항]", "[관련 링크 / 출처]", "[주의사항]"};
        int cursor = -1;
        for (String h : headers) {
            int at = body.indexOf(h);
            assertThat(at).as("섹션 %s 존재", h).isGreaterThan(cursor);
            cursor = at;
        }
        assertThat(body).contains(JipijigiDraftComposer.AI_DISCLAIMER)
                .contains(JipijigiDraftComposer.NO_PREDICTION_NOTICE)
                .contains("저장된 발췌")
                .contains("https://www.newstartfund.or.kr/x")
                .contains("STEP2 신복위");
    }

    @Test
    @DisplayName("본문 UNREADABLE 질문은 제목 기준임을 명시하고 내용을 지어내지 않는다")
    void unreadableQuestionIsDeclared() {
        String body = composer.composeAnswerBody(pack(List.of(), List.of(), List.of("gap")),
                question(JipijigiQuestion.BodyStatus.UNREADABLE, null));

        assertThat(body).contains("UNREADABLE").contains("제목 기준만 정리");
        assertThat(body).doesNotContain("협약기관이 안 나와요");
    }

    @Test
    @DisplayName("내부 근거가 없으면 '확정 답변 없이' 문구로 시작한다")
    void noInternalEvidenceAvoidsDefinitiveAnswer() {
        String body = composer.composeAnswerBody(pack(List.of(), List.of(), List.of()),
                question(JipijigiQuestion.BodyStatus.READABLE, "질문 본문"));

        assertThat(body).contains("내부 DB에 이 질문을 직접 뒷받침하는 ACTIVE 자료가 아직 부족합니다")
                .contains("확정 답변 없이");
    }

    @Test
    @DisplayName("제목이 길면 잘라서하고 접두어를 붙인다")
    void titlePrefixAndTruncation() {
        JipijigiQuestion q = question(JipijigiQuestion.BodyStatus.READABLE, "본문");
        q.setTitle("가".repeat(90));

        String title = composer.composeAnswerTitle(q);

        assertThat(title).startsWith("지피지기 Agent가 답합니다 — ").hasSizeLessThanOrEqualTo("지피지기 Agent가 답합니다 — ".length() + 61);
    }
}
