package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.Evidence;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiResearchService.ResearchPack;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartKnowledgeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service.NewstartDataIngestionService.RepairIngestionResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import java.util.Optional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * §14-18 로컬 클론(f2r_db_repair) 종합 검증 — 운영 DB에서 실행 금지(환경변수 게이트).
 *
 * <p>repair import멱등/불변식 → REVIEW 검색 게이트 → approveReview 정상 워크플로(ACTIVE)
 * → 승인 후 검색 도달 → RAG 질문 A~E(공식/감면율사례/진행기간/추심지급명령/공식vs카페 상충)의
 *检索 도달·출처 라벨(공식 자료 vs 부실차주 카페 사례) 우선순위를 검증한다. LLM 호출은 없다
 * (조사 사다리+컨텍스트 조립까지가 이 테스트의 범위 — 답변 생성은 Wiki Router 소관).
 */
@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "spring.flyway.clean-disabled=true"
})
@EnabledIfEnvironmentVariable(named = "NEWSTART_REPAIR_MYSQL_TEST", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("NewStart 수리 import → 승인 → RAG 사다리 — 로컬 클론 종합 검증")
class NewstartRepairApprovalRagMySqlTest {

    private static final String REPAIR_DIR = "data/newstart-cafe/repair_staging/articles";
    /** EBK-5-1(연체 기산일·장기연체등록): 게이트 시험 대상 — 시험 중 REVIEW→ACTIVE 승인. */
    private static final long EBK51_ID = 6L;
    private static final String EBK51_MARKER = "EBK-5-1";

    @Autowired NewstartDataIngestionService ingestionService;
    @Autowired NewstartKnowledgeService knowledgeService;
    @Autowired NewstartKnowledgeItemRepository knowledgeRepository;
    @Autowired JipijigiResearchService researchService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Order(1)
    @DisplayName("given 수리 import가 반영된 클론 when 재실행 then failed 0·전부 skip(멱등)이고 dup0·ACTIVE 2건(수리 무관)·수리 row sourceUrl 불변")
    void repairIdempotentAndInvariantsHold() {
        RepairIngestionResult r = ingestionService.repairEmpiricalCases(REPAIR_DIR);
        assertThat(r.getFailed()).isZero();
        assertThat(r.getInserted()).isZero();
        assertThat(r.getUpdated()).isZero();
        assertThat(r.getSkipped()).isEqualTo(58);

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM newstart_empirical_cases", Integer.class);
        Integer distinct = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT article_id) FROM newstart_empirical_cases", Integer.class);
        assertThat(rows).isEqualTo(366).isEqualTo(distinct);

        // 체인 보존: 수리한 6건의 contentRaw 길이가 staging 전문과 일치(축소 없음)
        assertThat(jdbc.queryForObject(
                "SELECT CHAR_LENGTH(content_raw) FROM newstart_empirical_cases WHERE article_id='5971'",
                Integer.class)).isEqualTo(503);
        // cross-cafe identity: 원 row의 newhopero URL은 병합 대상이 아니라 불변
        assertThat(jdbc.queryForObject(
                "SELECT source_url FROM newstart_empirical_cases WHERE article_id='5870'", String.class))
                .contains("newhopero");
        // followUpResult 보존: staging이 빈 값이어도 기존 값을 지우지 않는다
        assertThat(jdbc.queryForObject(
                "SELECT CHAR_LENGTH(COALESCE(author_followup_result,'')) FROM newstart_empirical_cases "
                        + "WHERE article_id='5870'", Integer.class)).isGreaterThan(0);
    }

    @Test
    @Order(2)
    @DisplayName("given EBK-5-1이 REVIEW(이거나 시험 전 상태) when research then 검색 근거에 노출되지 않는다 — ACTIVE 필터")
    void reviewItemNotRetrieved() {
        Optional<NewstartKnowledgeItem> item = knowledgeRepository.findById(EBK51_ID);
        assertThat(item).isPresent();
        if (item.get().getStatus() != NewstartKnowledgeItem.KnowledgeStatus.REVIEW) {
            return; // 재실행: 이미 ACTIVE면 게이트 단계는 스킵(3단계가 도달을 검증)
        }
        ResearchPack pack = researchService.research("연체 기산일은 언제부터인가요 장기연체등록 시점 확인");
        assertThat(titles(pack)).noneMatch(t -> t.contains(EBK51_MARKER));
    }

    @Test
    @Order(3)
    @DisplayName("given REVIEW인 EBK-5-1 when approveReview 정상 워크플로(경고 시 confirmed+사유) then ACTIVE 전환되고 검색에 도달한다")
    void approveThenRetrieved() {
        NewstartKnowledgeItem item = knowledgeRepository.findById(EBK51_ID).orElseThrow();
        if (item.getStatus() == NewstartKnowledgeItem.KnowledgeStatus.REVIEW) {
            try {
                knowledgeService.approveReview(EBK51_ID, "admin", "RAG 검증 승인(클론 시험)", false);
            } catch (NewstartKnowledgeService.ReviewConfirmationRequiredException expectedConflict) {
                knowledgeService.approveReview(EBK51_ID, "admin", "RAG 검증 승인(클론 시험)", true);
            }
        }
        item = knowledgeRepository.findById(EBK51_ID).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE);

        ResearchPack pack = researchService.research("연체 기산일은 언제부터인가요 장기연체등록 시점 확인");
        assertThat(titles(pack)).anyMatch(t -> t.contains(EBK51_MARKER));
    }

    @Test
    @Order(4)
    @DisplayName("given RAG 질문 A~E when 조사 사다리 then 공식은 '공식 자료'·수리된 카페 사례는 '부실차주 카페 사례'로 라벨링되어 조립 컨텍스트에 들어간다")
    void ragQuestionsAtoERetrieveGroundedEvidence() {
        // A 공식 기준
        List<Evidence> a = ordered("부실우려차주 연체 기준은 어떻게 되나요");
        assertThat(labels(a)).contains("공식 자료");

        // B 감면율 사례(수리·수집된 실증 사례)
        List<Evidence> b = ordered("감면율 확정 사례가 있나요");
        assertThat(labels(b)).anyMatch(l -> l.equals("실증 사례(DB)") || l.equals("부실차주 카페 사례"));

        // C 진행 기간
        List<Evidence> c = ordered("임시안 체결까지 얼마나 걸리나요 진행 기간");
        assertThat(c).isNotEmpty();

        // D 추심·지급명령
        List<Evidence> d = ordered("지급명령이 도착했는데 어떻게 대응하나요 추심");
        assertThat(d).isNotEmpty();

        // E 공식 vs 카페 상충: 90% 감면 주장 → 공식 상한 근거와 카페 사례가 모두 노출되되 우선순위는 공식
        List<Evidence> e = ordered("감면율 90프로까지 받는다는데 사실인가요");
        List<String> eLabels = labels(e);
        int official = eLabels.indexOf("공식 자료");
        int cafe = firstCafeIndex(eLabels);
        assertThat(official).isGreaterThanOrEqualTo(0);
        if (cafe >= 0) {
            assertThat(official).isLessThan(cafe);
        }
        String context = NewStartAiChatService.buildEvidenceContext(e);
        assertThat(context).contains("[공식 자료]");
        if (cafe >= 0) {
            // 조립 컨텍스트에서도 공식/카페 라벨이 분리 유지된다
            assertThat(context).contains("[" + eLabels.get(cafe) + "]");
        }
    }

    private List<Evidence> ordered(String question) {
        return NewStartAiChatService.orderedEvidence(researchService.research(question));
    }

    private static List<String> labels(List<Evidence> ev) {
        return ev.stream().map(e -> NewStartAiChatService.labelOf(e.getSourceClass())).toList();
    }

    private static int firstCafeIndex(List<String> labels) {
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).contains("카페")) return i;
        }
        return -1;
    }

    private static List<String> titles(ResearchPack pack) {
        return NewStartAiChatService.orderedEvidence(pack).stream().map(Evidence::getTitle).toList();
    }
}
