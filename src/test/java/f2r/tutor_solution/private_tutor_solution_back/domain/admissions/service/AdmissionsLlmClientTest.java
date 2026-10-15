package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm.NewStartLlmTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdmissionsLlmClient primary 선택 단위 테스트.
 * given/when/then 구조.
 */
class AdmissionsLlmClientTest {

    private NewStartLlmTransport mockTransport(String name) {
        NewStartLlmTransport t = mock(NewStartLlmTransport.class);
        when(t.name()).thenReturn(name);
        return t;
    }

    @Test
    @DisplayName("primary 선택 — 기본값은 wiki-router")
    void selectPrimary_defaultIsWikiRouter() {
        // given
        var wiki = mockTransport("wiki-router");
        var nvidia = mockTransport("nvidia");
        var meta = mockTransport("meta");

        // when
        var selected = AdmissionsLlmClient.selectPrimary(null, wiki, nvidia, meta);

        // then
        assertEquals("wiki-router", selected.name());
    }

    @Test
    @DisplayName("primary 선택 — meta 지정 시 Meta Muse Spark")
    void selectPrimary_metaSelectsMeta() {
        // given
        var wiki = mockTransport("wiki-router");
        var nvidia = mockTransport("nvidia");
        var meta = mockTransport("meta");

        // when
        var selected = AdmissionsLlmClient.selectPrimary("meta", wiki, nvidia, meta);

        // then
        assertEquals("meta", selected.name());
    }

    @Test
    @DisplayName("primary 선택 — nvidia 지정 시 NVIDIA NIM")
    void selectPrimary_nvidiaSelectsNvidia() {
        // given
        var wiki = mockTransport("wiki-router");
        var nvidia = mockTransport("nvidia");
        var meta = mockTransport("meta");

        // when
        var selected = AdmissionsLlmClient.selectPrimary("nvidia", wiki, nvidia, meta);

        // then
        assertEquals("nvidia", selected.name());
    }

    @Test
    @DisplayName("primary 선택 — 알 수 없는 값은 wiki-router로 폴백")
    void selectPrimary_unknownFallsBackToWikiRouter() {
        // given
        var wiki = mockTransport("wiki-router");
        var nvidia = mockTransport("nvidia");
        var meta = mockTransport("meta");

        // when
        var selected = AdmissionsLlmClient.selectPrimary("unknown-value", wiki, nvidia, meta);

        // then
        assertEquals("wiki-router", selected.name());
    }
}
