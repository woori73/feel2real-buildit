package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsCounselingSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 입시상담 공개 API 기본 OFF 계약 — 설정을 아무것도 주지 않은 실제 Spring 컨텍스트(운영 기본값과 동일)에서
 * {@code POST /api/admissions/chat}이 503이고 상담 이력(DB)에 아무것도 쓰지 않음을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
@DisplayName("입시상담 공개 API — 설정 없이 기동하면 비활성(503)")
class AdmissionsPublicApiDefaultOffMySqlTest {

  @Autowired MockMvc mvc;
  @Autowired AdmissionsCounselingSessionRepository sessions;

  @Test
  @DisplayName("given 설정 기본값 when 비회원이 chat을 호출하면 then 503 + 안내, 상담 이력은 늘지 않는다")
  void chat_isDisabledByDefault() throws Exception {
    // given
    long before = sessions.count();

    // when
    MvcResult result = mvc.perform(post("/api/admissions/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"2027 서울대 일정 알려줘\",\"academicYear\":2027}"))
        .andReturn();

    // then
    assertThat(result.getResponse().getStatus()).isEqualTo(503);
    assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .contains("\"status\":\"error\"").contains("아직 이용할 수 없습니다");
    assertThat(sessions.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("given 설정 기본값 when 안내 화면(/admissions/guide)을 열면 then 화면은 그대로 200 (API만 막힘)")
  void guidePage_stillRenders() throws Exception {
    // given / when
    MvcResult result = mvc.perform(get("/admissions/guide")).andReturn();

    // then
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }
}
