package f2r.tutor_solution.private_tutor_solution_back.domain.study;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class StudyHubControllerTest {
    @Test
    void listsCatalogAndLoadsInitialExercise() throws Exception {
        var hub = new StudyHubController(JsonMapper.builder().build());
        var model = new ExtendedModelMap();
        assertEquals("public/study/index", hub.index(model));
        assertEquals(300, model.get("count"));
        assertEquals("public/study/detail", hub.topic("ms-en-relative", model));
        assertEquals("public/study/detail", hub.daily("ms-en-relative", "2026-11-24", model));
        assertEquals("/study/ms-en-relative/2026-11-24", model.get("canonical"));
        assertThrows(ResponseStatusException.class, () -> hub.topic("missing-topic", model));
        assertThrows(ResponseStatusException.class, () -> hub.daily("ms-en-relative", "1900-01-01", model));
    }
}
