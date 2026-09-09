package f2r.tutor_solution.private_tutor_solution_back.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EduholicActualClassHeroIsolationContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void guardLoadsAfterSectionsAndBeforePublicRenderer() throws Exception {
        String html = Files.readString(RESOURCES.resolve("templates/public/academy_public.html"));
        int sections = html.indexOf("/js/academy_sections.js");
        int guard = html.indexOf("/js/eduholic_actual_class_video_filter.js");
        int publicRenderer = html.indexOf("/js/academy_public.js");
        assertThat(guard).isGreaterThan(sections);
        assertThat(publicRenderer).isGreaterThan(guard);
    }

    @Test
    void guardIsEduholicOnlyAndUsesTheSameDedicatedHeroMarker() throws Exception {
        String guard = Files.readString(RESOURCES.resolve("static/js/eduholic_actual_class_video_filter.js"));
        String hero = Files.readString(RESOURCES.resolve("static/js/eduholic_midterm_home_live.js"));
        assertThat(guard).contains("eduholic.feel2real.co.kr");
        assertThat(guard).contains("내신대비_라이브_최종_53초");
        assertThat(hero).contains("내신대비_라이브_최종_53초");
    }
}
