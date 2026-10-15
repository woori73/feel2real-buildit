package f2r.tutor_solution.private_tutor_solution_back.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PageNotFoundRedirectControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PageNotFoundRedirectController())
            .build();

    @Test
    @DisplayName("GET 404 general page route shows 404 page (not redirect to /home)")
    void get_404_general_page_route_shows_404() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/unknown/page"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    @DisplayName("HEAD 404 general page route shows 404 status (not redirect to /home)")
    void head_404_general_page_route_shows_404() throws Exception {
        mockMvc.perform(head("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/unknown/page"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API 404 keeps the original 404 status")
    void api_404_keeps_original_status() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Static resource 404 keeps the original 404 status")
    void static_404_keeps_original_status() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/css/missing.css"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Last-segment file extension 404 keeps the original 404 status")
    void file_extension_404_keeps_original_status() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing/file.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Non-GET 404 keeps the original 404 status")
    void non_get_404_keeps_original_status() throws Exception {
        mockMvc.perform(post("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/unknown/page"))
                .andExpect(status().isNotFound());
    }
}
