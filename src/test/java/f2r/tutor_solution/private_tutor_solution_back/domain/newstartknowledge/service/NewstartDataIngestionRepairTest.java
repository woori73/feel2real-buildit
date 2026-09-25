package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartEmpiricalCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartEmpiricalCaseRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * §14 수리 import 계약: 대상 articleId만 비파괴 병합 — contentRaw는 더 완전한 쪽으로만 교체(체인 verbatim),
 * 그 외 필드는 빈 값만 채우고 non-null은 절대 덮어쓰지 않는다(prediction→confirmed 변환 금지).
 * knowledge 테이블 무접근(REVIEW 승격 우회 경로 없음), 멱등(2회 실행 시 전부 skip).
 */
@DisplayName("NewstartDataIngestionService.repairEmpiricalCases — 8·9월 정합성 복구 병합 계약")
class NewstartDataIngestionRepairTest {

    private NewstartEmpiricalCaseRepository cases;
    private NewstartKnowledgeItemRepository knowledge;
    private NewstartDataIngestionService service;
    private Map<String, NewstartEmpiricalCase> store;

    @BeforeEach
    void setUp() {
        cases = mock(NewstartEmpiricalCaseRepository.class);
        knowledge = mock(NewstartKnowledgeItemRepository.class);
        service = new NewstartDataIngestionService(cases, knowledge);
        store = new LinkedHashMap<>();
        when(cases.findByArticleId(any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
        when(cases.save(any(NewstartEmpiricalCase.class))).thenAnswer(inv -> {
            NewstartEmpiricalCase e = inv.getArgument(0);
            store.put(e.getArticleId(), e);
            return e;
        });
    }

    private void stage(@TempDir Path dir, String articleId, Map<String, Object> fields) throws Exception {
        Map<String, Object> node = new LinkedHashMap<>(fields);
        node.put("articleId", articleId);
        Files.writeString(dir.resolve("article_" + articleId + ".json"),
                new ObjectMapper().writeValueAsString(node));
    }

    private static Map<String, Object> base(String title, String contentRaw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("boardName", "자유게시판");
        m.put("contentRaw", contentRaw);
        m.put("sourceUrl", "https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid=X");
        return m;
    }

    @Test
    @DisplayName("given 행이 없음 when repair then 신규 insert되고 createdBy/updatedBy가 REPAIR_BOT이며 structured 필드가 매핑된다")
    void missingRowInsertedWithRepairProvenance(@TempDir Path dir) throws Exception {
        Map<String, Object> f = base("장기연체코드", "전문 본문");
        f.put("age", 44);
        f.put("debtTotal", 30000000L);
        f.put("householdType", "1인가구");
        stage(dir, "5967", f);

        var result = service.repairEmpiricalCases(dir.toString());

        assertThat(result.getInserted()).isEqualTo(1);
        assertThat(result.getUpdated()).isZero();
        assertThat(result.getFailed()).isZero();
        NewstartEmpiricalCase saved = store.get("5967");
        assertThat(saved.getCreatedBy()).isEqualTo("REPAIR_BOT");
        assertThat(saved.getUpdatedBy()).isEqualTo("REPAIR_BOT");
        assertThat(saved.getAge()).isEqualTo(44);
        assertThat(saved.getDebtTotal()).isEqualTo(30000000L);
        assertThat(saved.getHouseholdType()).isEqualTo("1인가구");
    }

    @Test
    @DisplayName("given 잘린 contentRaw의 INCOMPLETE 행 when repair then 본문+댓글+후속 verbatim 전문으로 교체되고 빈 필드만 채워진다")
    void truncatedRowRepairedWithFullChain(@TempDir Path dir) throws Exception {
        NewstartEmpiricalCase existing = NewstartEmpiricalCase.builder()
                .articleId("5870").boardName("자유게시판").title("부실우려 신청 완료")
                .contentRaw("8월7일 부실우려로 신청 완료.")
                .authorFollowupResult(null)
                .predictedReductionRate("70")
                .createdBy("INGESTION_BOT")
                .build();
        store.put("5870", existing);
        Map<String, Object> f = base("부실우려 신청 완료",
                "8월7일 부실우려로 신청 완료.\n[댓글 요지] 심사 일정 안내\n[추가] TIMELINE_DATA: 8/7 신청");
        f.put("authorFollowupResult", "임시안 확정 대기 중");
        stage(dir, "5870", f);

        var result = service.repairEmpiricalCases(dir.toString());

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getInserted()).isZero();
        assertThat(result.getUpdatedArticleIds()).containsExactly("5870");
        assertThat(existing.getContentRaw())
                .contains("[댓글 요지]").contains("TIMELINE_DATA");
        assertThat(existing.getAuthorFollowupResult()).isEqualTo("임시안 확정 대기 중");
        assertThat(existing.getUpdatedBy()).isEqualTo("REPAIR_BOT");
        // 빈 필드 채우기만 있었지 non-null 덮어쓰기는 없다:
        assertThat(existing.getPredictedReductionRate()).isEqualTo("70");
    }

    @Test
    @DisplayName("given staged가 기존과 동일/짧음 when repair then contentRaw 축소 금지, save 없음(멱등)")
    void equalOrShorterContentIsNeverDowngraded(@TempDir Path dir) throws Exception {
        NewstartEmpiricalCase existing = NewstartEmpiricalCase.builder()
                .articleId("5971").boardName("부실차주 신청").title("가압류 재산공제")
                .sourceUrl("https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid=X")
                .contentRaw("매우 완전한 전문 — 기존 DB가 더 길다. ".repeat(30))
                .build();
        store.put("5971", existing);
        String before = existing.getContentRaw();
        stage(dir, "5971", base("가압류 재산공제", "짧은 전문"));

        var result = service.repairEmpiricalCases(dir.toString());

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getUpdated()).isZero();
        assertThat(existing.getContentRaw()).isEqualTo(before);
        verify(cases, never()).save(any());
    }

    @Test
    @DisplayName("given 이미 값 있는 prediction/final 필드 when repair then staged 값으로 절대 덮어쓰지 않는다(prediction→confirmed 변환 금지)")
    void nonNullStructuredFieldsNeverOverwritten(@TempDir Path dir) throws Exception {
        NewstartEmpiricalCase existing = NewstartEmpiricalCase.builder()
                .articleId("5955").boardName("부실차주 임시안부터 확정안").title("임시안")
                .contentRaw("기존 전문")
                .predictedReductionRate("65")
                .finalReductionRate("74")
                .build();
        store.put("5955", existing);
        Map<String, Object> f = base("임시안", "기존 전문보다 긴 수리 전문입니다.");
        f.put("predictedReductionRate", "90");
        f.put("finalReductionRate", "90");
        stage(dir, "5955", f);

        service.repairEmpiricalCases(dir.toString());

        NewstartEmpiricalCase after = store.get("5955");
        assertThat(after.getPredictedReductionRate()).isEqualTo("65");
        assertThat(after.getFinalReductionRate()).isEqualTo("74");
    }

    @Test
    @DisplayName("given 두 번째 실행 when repair then 전부 skipped — 재실행 멱등(중복 ingestion 보호)")
    void secondRunIsIdempotent(@TempDir Path dir) throws Exception {
        stage(dir, "5956", base("감면 프로그램 문의", "긴 전문"));
        service.repairEmpiricalCases(dir.toString());
        int size = store.size();

        var second = service.repairEmpiricalCases(dir.toString());

        assertThat(second.getInserted()).isZero();
        assertThat(second.getUpdated()).isZero();
        assertThat(second.getSkipped()).isEqualTo(1);
        assertThat(store).hasSize(size);
    }

    @Test
    @DisplayName("given repair 실행 when 어떤 경로든 then knowledge 리포지토리는 전혀 접근하지 않는다 — REVIEW 승격 우회 경로 없음")
    void repairNeverTouchesKnowledgeTable(@TempDir Path dir) throws Exception {
        stage(dir, "5863", base("안녕하세요", "전문"));

        service.repairEmpiricalCases(dir.toString());

        verifyNoInteractions(knowledge);
    }

    @Test
    @DisplayName("given 대상 목록의 articleId when repair then 대상 아닌 행(store의 그 외 row)은 조회·저장 대상으로 삼지 않는다")
    void outOfScopeRowsUntouched(@TempDir Path dir) throws Exception {
        NewstartEmpiricalCase otherCafe = NewstartEmpiricalCase.builder()
                .articleId("7777").boardName("신복위 상담").title("다른 카페 기사").contentRaw("원문").build();
        store.put("7777", otherCafe);
        ArgumentCaptor<String> looked = ArgumentCaptor.forClass(String.class);
        stage(dir, "5859", base("가입인사", "전문"));

        var result = service.repairEmpiricalCases(dir.toString());

        assertThat(result.getInserted()).isEqualTo(1);
        verify(cases, never()).save(otherCafe);
        verify(cases, org.mockito.Mockito.atLeastOnce()).findByArticleId(looked.capture());
        assertThat(looked.getAllValues()).doesNotContain("7777");
        assertThat(otherCafe.getContentRaw()).isEqualTo("원문");
    }
}
