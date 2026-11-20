package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighEnglishStore;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighGrammarBank;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.PreHighPart2Store;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PreHighPart2PaperPdfTest {
  private static final String PATH = "/api/admin/pre-high-english/part2/{round}/paper.pdf";
  @TempDir Path temporary;
  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    var mapper = new ObjectMapper();
    var controller = new PreHighEnglishController(new PreHighEnglishStore(mapper, temporary.toString()),
        new PreHighPart2Store(mapper), new PreHighGrammarBank(mapper), List.of("https://feel2real.co.kr"));
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("given 관리자, when 2부 문제지 PDF를 요청하면, then PDF 파일을 내려받는다")
  void administratorDownloadsPaper() throws Exception {
    // given
    authenticate("ADMIN");
    // when
    byte[] pdf = mvc.perform(get(PATH, 4)).andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
    // then
    assertThat(pdf).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
  }

  @Test
  @DisplayName("given 비회원·학생·강사, when 2부 문제지 PDF를 직접 요청하면, then 모두 403으로 막힌다")
  void othersAreForbidden() throws Exception {
    // given / when / then
    mvc.perform(get(PATH, 1)).andExpect(status().isForbidden());
    authenticate("STUDENT");
    mvc.perform(get(PATH, 1)).andExpect(status().isForbidden());
    authenticate("TUTOR");
    mvc.perform(get(PATH, 1)).andExpect(status().isForbidden());
  }

  private void authenticate(String role) {
    var principal = new AuthenticatedPrincipal(23L, "part2-pdf-test", role, "");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, "", List.of()));
  }
}
