package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.GeminiProblemGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.LunaProblemTransformationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemGeneratorAiStatusController {

  @org.springframework.beans.factory.annotation.Value("${SITEWIDE_LUNA_ENABLED:false}")
  private boolean sitewideLunaEnabled;

  @GetMapping("/api/problem-generator/ai/status")
  public ResponseEntity<Map<String, Object>> status() {
    boolean geminiReady = present(sitewideLunaEnabled ? "OPENAI_API_KEY" : "GEMINI_API_KEY");
    boolean lunaReady = present("OPENAI_API_KEY");
    return ResponseEntity.ok(Map.of(
        "gemini", Map.of("model", sitewideLunaEnabled ? LunaProblemTransformationService.MODEL : GeminiProblemGenerationService.MODEL, "ready", geminiReady, "role", "generation"),
        "luna", Map.of("model", LunaProblemTransformationService.MODEL, "ready", lunaReady, "role", "transformation"),
        "ready", geminiReady && lunaReady));
  }

  private boolean present(String name) {
    String value = System.getenv(name);
    return value != null && !value.isBlank();
  }
}
