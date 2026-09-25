package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 지피지기 Agent ADMIN 화면 계약: 4개 수동 명령 버튼, allowlist/추적 노출,
 * 그리고 "직접 게시하지 않는다"는 문구가 화면에 실제로 존재하는지 검증한다.
 */
@DisplayName("지피지기 Agent ADMIN 화면 계약")
class JipijigiAdminPageContractTest {

    private String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void pageExposesFourManualCommandsAndSafetyNotes() throws Exception {
        String html = read("templates/admin/newstart_jipijigi.html");

        assertThat(html)
                .contains("최근 질문 5개 조사")
                .contains("신복위 카페 사례 조사")
                .contains("Agent 소개글 작성")
                .contains("직접 명령 입력")
                .contains("STEP1 우리 DB/RAG")
                .contains("NAVER 실제 게시를 수행하지 않습니다")
                .contains("allowlist")
                .contains("/js/admin_newstart_jipijigi.js")
                .contains("fragments/_admin_topnav :: topnav('jipijigiAgent')");
    }

    @Test
    void jsCallsOnlyAdminApiAndShowsTraceAudit() throws Exception {
        String js = read("static/js/admin_newstart_jipijigi.js");

        assertThat(js)
                .contains("/api/admin/newstart/jipijigi")
                .contains("/inventory")
                .contains("/commands")
                .contains("/boards")
                .contains("/drafts")
                .contains("ensureCurrentUser")
                .contains("currentUser.role !== 'ADMIN'")
                .contains("menuid 확인 필요");
    }

    @Test
    void topnavLinksToAgentScreen() throws Exception {
        assertThat(read("templates/fragments/_admin_topnav.html"))
                .contains("/admin/newstart/jipijigi");
    }
}
