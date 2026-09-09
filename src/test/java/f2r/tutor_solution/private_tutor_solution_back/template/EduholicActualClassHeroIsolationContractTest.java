package f2r.tutor_solution.private_tutor_solution_back.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EduholicActualClassHeroIsolationContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void heroGuardLoadsAfterSectionsAndBeforePublicRenderer() throws Exception {
        String html = Files.readString(RESOURCES.resolve("templates/public/academy_public.html"));
        int sections = html.indexOf("/js/academy_sections.js");
        int guard = html.indexOf("/js/eduholic_actual_class_video_filter.js");
        int publicRenderer = html.indexOf("/js/academy_public.js");

        assertThat(sections).isGreaterThanOrEqualTo(0);
        assertThat(guard).isGreaterThan(sections);
        assertThat(publicRenderer).isGreaterThan(guard);
    }

    @Test
    void guardIsEduholicOnlyAndExcludesTheHomepageHeroMarker() throws Exception {
        String js = Files.readString(RESOURCES.resolve("static/js/eduholic_actual_class_video_filter.js"));

        assertThat(js).contains("eduholic.feel2real.co.kr");
        assertThat(js).contains("내신대비_라이브_최종_53초");
        assertThat(js).contains("sections.renderVideos");
        assertThat(js).contains("videos.filter");
    }
}
