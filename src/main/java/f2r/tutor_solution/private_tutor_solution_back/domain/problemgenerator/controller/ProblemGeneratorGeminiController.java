package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiChatRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiChatResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiGenerateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiGenerateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.GeminiProblemGenerationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemGeneratorGeminiController {
  private static final Logger log = LoggerFactory.getLogger(ProblemGeneratorGeminiController.class);
  private final GeminiProblemGenerationService service;

  public ProblemGeneratorGeminiController(GeminiProblemGenerationService service) {
    this.service = service;
  }

  @PostMapping("/api/problem-generator/gemini/chat")
  public ResponseEntity<GeminiChatResponse> chat(@RequestBody GeminiChatRequest request) {
    String text = service.chat(request.apiKey(), request.message(), request.context());
    return ResponseEntity.ok(new GeminiChatResponse("gemini-3.6-flash", text));
  }

  @PostMapping("/api/problem-generator/gemini/generate")
  public ResponseEntity<GeminiGenerateResponse> generate(@RequestBody GeminiGenerateRequest request) {
    int count = request.count() == null ? 3 : request.count();
    String text = service.generate(
        request.apiKey(),
        request.grammar(),
        request.passage(),
        count,
        request.difficulty(),
        request.materialTitle());
    return ResponseEntity.ok(new GeminiGenerateResponse("gemini-3.6-flash", text));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("detail", exception.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> badGateway(IllegalStateException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> serverError(Exception exception) {
    log.warn("Problem generator Gemini request failed", exception);
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      message = exception.getClass().getSimpleName();
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("detail", "Gemini 채팅 처리 중 서버 오류: " + message));
  }
}
