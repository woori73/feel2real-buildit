package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 기본 설정(스위치 OFF)에서는 새 주소가 모두 닫혀 있어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("수강료 청구 링크 — 기본값(스위치 OFF)")
class TuitionBillingDisabledMySqlTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;

  @Test
  @DisplayName("given 기본 설정 when 공개 주소·관리 API 호출 then 모두 404이고 status만 꺼짐을 알려 준다")
  void everythingIsClosedByDefault() throws Exception {
    // given
    Cookie admin = new Cookie("ACCESS_TOKEN", tokenProvider.issue(2L, "admin", "ADMIN"));
    String token = TuitionBillTokens.newToken();
    // when / then
    mockMvc.perform(get("/pay/" + token)).andExpect(status().isNotFound());
    mockMvc.perform(post("/pay/" + token + "/checkout")).andExpect(status().isNotFound());
    mockMvc.perform(get("/pay/" + token + "/success").param("orderId", "o")
        .param("paymentKey", "test_ok_o").param("amount", "1")).andExpect(status().isNotFound());
    mockMvc.perform(post("/pay/notify").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/admin/easy/billing/links").param("month", "2026-11").cookie(admin))
        .andExpect(status().isNotFound());
    mockMvc.perform(post("/api/admin/easy/billing/matches/run").cookie(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/admin/easy/billing/status").cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.cardTest").value(false));
    mockMvc.perform(get("/api/admin/easy/billing/status")).andExpect(status().isUnauthorized());
  }
}
