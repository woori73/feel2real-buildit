package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.NewStartLlmProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.N8nProblemGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.GeminiProblemGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartGeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SitewideLunaRoutingTest {
  private OpenAiLunaLlmTransport enable(Object service) {
    var luna = mock(OpenAiLunaLlmTransport.class);
    ReflectionTestUtils.setField(service, "sitewideLuna", luna);
    ReflectionTestUtils.setField(service, "sitewideLunaEnabled", true);
    return luna;
  }

  @Test
  void givenSitewideFlag_whenLegacyProvidersCalled_thenOnlyLunaReceivesOriginalContext() {
    var props = new NewStartLlmProperties();
    for (NewStartLlmTransport service : new NewStartLlmTransport[]{new MetaMuseSparkLlmTransport(props),
        new NvidiaNimLlmTransport(props), new OpenRouterQwenLlmTransport(props)}) {
      var luna = enable(service);
      var expected = NewStartLlmCompletion.success("answer", "openai-luna", "gpt-6-luna");
      when(luna.complete("policy", "context", 1000)).thenReturn(expected);
      assertSame(expected, service.complete("policy", "context", 1000));
      assertEquals("openai-luna", service.name());
      verify(luna, times(1)).complete("policy", "context", 1000);
    }
  }

  @Test
  void givenGenerationWithNoWebhook_whenLunaEnabled_thenProducesCompatibleQuestionsAndBlocksMalformedOutput() {
    var service = new N8nProblemGenerationService("", 1000, 1000);
    var luna = enable(service);
    when(luna.complete(anyString(), anyString(), eq(24000))).thenReturn(NewStartLlmCompletion.success("""
        {"summary":"one question","questions":[{"type":"서답형","stem":"Translate.","answer":"답","explanation":"해설"}]}
        """, "openai-luna", "gpt-6-luna"));
    assertTrue(service.generate("school", "grade", "exam", "grammar", "text", 1, "normal").contains("Translate."));
    assertEquals("gpt-6-luna", service.model());
    when(luna.complete(anyString(), anyString(), eq(24000))).thenReturn(NewStartLlmCompletion.success(
        "{\"questions\":[{}]}", "openai-luna", "gpt-6-luna"));
    assertThrows(IllegalStateException.class, () -> service.generate("school", "grade", "exam", "grammar", "", 1, "normal"));
    when(luna.complete(anyString(), anyString(), eq(24000))).thenReturn(NewStartLlmCompletion.success(
        "{\"questions\":[]}", "openai-luna", "gpt-6-luna"));
    assertThrows(IllegalStateException.class, () -> service.generate("school", "grade", "exam", "grammar", "", 1, "normal"));
  }

  @Test
  void givenChartRuntimeGeminiKey_whenLunaEnabled_thenReportsServerOpenAiConfiguration() {
    var service = new ChartGeminiClient();
    ReflectionTestUtils.setField(service, "sitewideLunaEnabled", true);
    ReflectionTestUtils.setField(service, "openaiApiKey", "test-server-only");
    ReflectionTestUtils.setField(service, "runtimeApiKey", "test-gemini-only");
    assertEquals("openai", service.provider());
    assertEquals("gpt-6-luna", service.model());
    assertEquals("environment", service.apiKeySource());
    assertEquals("test-server-only", ReflectionTestUtils.invokeMethod(service, "resolvedApiKey"));
  }
}
