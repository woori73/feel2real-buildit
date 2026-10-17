package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightException;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("기출분석 API — 사진·HTML 업로드 계약")
@WebMvcTest(controllers = ExamInsightController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class ExamInsightControllerWebMvcTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtTokenProvider tokenProvider;
  @MockitoBean ExamInsightService service;

  private Cookie cookie(String role) {
    return new Cookie("ACCESS_TOKEN", tokenProvider.issue(7L, "u-" + role, role));
  }

  private void config(boolean images) {
    when(service.isEnabled()).thenReturn(true);
    when(service.imageAnalysisAvailable()).thenReturn(images);
    when(service.maxFilesPerGroup()).thenReturn(20);
    when(service.maxImagesPerRequest()).thenReturn(20);
    when(service.maxImageBytes()).thenReturn(10L * 1024 * 1024);
    when(service.maxCharsPerGroup()).thenReturn(40000);
    when(service.imageDailyLimit()).thenReturn(60);
    when(service.remainingImagesToday(7L)).thenReturn(55);
    when(service.perUserDailyLimit()).thenReturn(3);
    when(service.remainingToday(7L)).thenReturn(2);
  }

  @Test
  @DisplayName("config: 사진 분석이 켜져 있으면 문서·웹 문서·사진 형식과 한도를 알리고, HEIC는 미지원으로 밝힌다")
  void config_withVision_listsAllFormats() throws Exception {
    // given
    config(true);

    // when / then
    mockMvc.perform(get("/api/exam-insight/config").cookie(cookie("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageAnalysis").value(true))
        .andExpect(jsonPath("$.documentFormats[0]").value("pdf"))
        .andExpect(jsonPath("$.webFormats[0]").value("html"))
        .andExpect(jsonPath("$.webFormats[1]").value("htm"))
        .andExpect(jsonPath("$.imageFormats[0]").value("jpg"))
        .andExpect(jsonPath("$.imageFormats[3]").value("webp"))
        .andExpect(jsonPath("$.supportedFormats[?(@=='html')]").exists())
        .andExpect(jsonPath("$.supportedFormats[?(@=='png')]").exists())
        .andExpect(jsonPath("$.heicSupported").value(false))
        .andExpect(jsonPath("$.maxFilesPerGroup").value(20))
        .andExpect(jsonPath("$.maxImagesPerRequest").value(20))
        .andExpect(jsonPath("$.remainingImagesToday").value(55));
  }

  @Test
  @DisplayName("config: 사진 분석이 꺼져 있으면 사진을 지원 형식에 넣지 않고 미지원 사유로 알린다 (읽는 척하지 않는다)")
  void config_withoutVision_doesNotAdvertisePhotos() throws Exception {
    // given
    config(false);

    // when / then
    mockMvc.perform(get("/api/exam-insight/config").cookie(cookie("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageAnalysis").value(false))
        .andExpect(jsonPath("$.imageFormats").isEmpty())
        .andExpect(jsonPath("$.supportedFormats[?(@=='png')]").doesNotExist())
        .andExpect(jsonPath("$.supportedFormats[?(@=='html')]").exists())
        .andExpect(jsonPath("$.unsupported[0]").value("사진(JPG·PNG·WebP) — 현재 사진 분석을 사용할 수 없어요"));
  }

  @Test
  @DisplayName("extract: 비로그인은 401")
  void extract_anonymous_is401() throws Exception {
    mockMvc.perform(multipart("/api/exam-insight/extract").file(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[] {1})))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("extract: 로그인한 사용자가 올린 파일들과 그룹이 서비스로 전달되고 파일별 결과가 그대로 응답된다")
  void extract_passesFilesAndReturnsPerFileResults() throws Exception {
    // given
    var ok = new ExamInsightService.ExtractedItem("a.jpg", "image", "ok", "1. Q", 1, 4, null, null, List.of());
    var bad = new ExamInsightService.ExtractedItem("b.html", "document", "error", "", 0, 0, "EMPTY_TEXT", "글자를 찾지 못했어요", List.of());
    when(service.extract(eq(7L), eq("scope"), any())).thenReturn(new ExamInsightService.ExtractResult(List.of(ok, bad), 54));

    // when / then
    mockMvc.perform(multipart("/api/exam-insight/extract")
            .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[] {1, 2}))
            .file(new MockMultipartFile("files", "b.html", "text/html", "<p></p>".getBytes()))
            .param("group", "scope")
            .cookie(cookie("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("done"))
        .andExpect(jsonPath("$.items[0].name").value("a.jpg"))
        .andExpect(jsonPath("$.items[0].kind").value("image"))
        .andExpect(jsonPath("$.items[0].uncertainCount").value(1))
        .andExpect(jsonPath("$.items[1].status").value("error"))
        .andExpect(jsonPath("$.items[1].code").value("EMPTY_TEXT"))
        .andExpect(jsonPath("$.remainingImagesToday").value(54));
    ArgumentCaptor<List<org.springframework.web.multipart.MultipartFile>> files = ArgumentCaptor.forClass(List.class);
    verify(service).extract(eq(7L), eq("scope"), files.capture());
    org.assertj.core.api.Assertions.assertThat(files.getValue()).hasSize(2);
  }

  @Test
  @DisplayName("extract: 서비스가 던진 오류 코드와 메시지가 상태 코드와 함께 그대로 나간다")
  void extract_serviceError_isMappedToStatusAndCode() throws Exception {
    // given
    when(service.extract(anyLong(), any(), any()))
        .thenThrow(new ExamInsightException(HttpStatus.TOO_MANY_REQUESTS, "IMAGE_LIMIT", "오늘 사진 한도를 모두 사용했어요."));

    // when / then
    mockMvc.perform(multipart("/api/exam-insight/extract")
            .file(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[] {1})).cookie(cookie("TUTOR")))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.code").value("IMAGE_LIMIT"));
  }

  @Test
  @DisplayName("analyze: 화면에서 고친 pastItems·scopeItems JSON이 순서대로 서비스에 전달된다")
  void analyze_parsesReviewedItems() throws Exception {
    // given
    when(service.analyze(anyLong(), any())).thenThrow(new ExamInsightException(HttpStatus.SERVICE_UNAVAILABLE, "DISABLED", "off"));

    // when
    mockMvc.perform(multipart("/api/exam-insight/analyze")
        .param("pastItems", "[{\"label\":\"[사진] p1.jpg\",\"text\":\"FIRST\"},{\"label\":\"[사진] p2.jpg\",\"text\":\"SECOND\"}]")
        .param("scopeItems", "[{\"label\":\"[HTML] s.html\",\"text\":\"SCOPE\"}]")
        .cookie(cookie("STUDENT")));

    // then
    ArgumentCaptor<ExamInsightService.Input> in = ArgumentCaptor.forClass(ExamInsightService.Input.class);
    verify(service).analyze(eq(7L), in.capture());
    org.assertj.core.api.Assertions.assertThat(in.getValue().pastItems()).extracting(ExamInsightService.Item::text).containsExactly("FIRST", "SECOND");
    org.assertj.core.api.Assertions.assertThat(in.getValue().scopeItems().get(0).label()).isEqualTo("[HTML] s.html");
  }

  @Test
  @DisplayName("analyze: items JSON이 깨졌거나 너무 크면 400이고 분석을 시작하지 않는다")
  void analyze_invalidItemsJson_is400() throws Exception {
    mockMvc.perform(multipart("/api/exam-insight/analyze").param("pastItems", "{not json").cookie(cookie("STUDENT")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ITEMS"));
    mockMvc.perform(multipart("/api/exam-insight/analyze").param("pastItems", "x".repeat(1_000_001)).cookie(cookie("STUDENT")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ITEMS_TOO_LARGE"));
  }
}
