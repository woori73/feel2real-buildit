package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiChatRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiChatResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiGenerateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.GeminiGenerateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.GeminiProblemGenerationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.N8nProblemGenerationService;
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
  // 박원장 AI 채팅 도우미(/gemini/chat)는 여전히 Gemini API를 직접 호출한다(GEMINI_API_KEY 서버 환경변수 사용).
  private final GeminiProblemGenerationService chatService;
  // 문제 생성(/gemini/generate)은 n8n Production Webhook을 거친다. 브라우저는 더 이상
  // Gemini API 키를 다루지 않고, n8n 주소도 이 서버 안에서만 사용된다.
  private final N8nProblemGenerationService generationService;

  public ProblemGeneratorGeminiController(GeminiProblemGenerationService chatService,
      N8nProblemGenerationService generationService) {
    this.chatService = chatService;
    this.generationService = generationService;
  }

  @PostMapping("/api/problem-generator/gemini/chat")
  public ResponseEntity<GeminiChatResponse> chat(@RequestBody GeminiChatRequest request) {
    String text = chatService.chat(request.apiKey(), request.message(), request.context());
    return ResponseEntity.ok(new GeminiChatResponse(GeminiProblemGenerationService.MODEL, text));
  }

  @PostMapping("/api/problem-generator/gemini/generate")
  public ResponseEntity<GeminiGenerateResponse> generate(@RequestBody GeminiGenerateRequest request) {
    String text = generationService.generate(
        request.school(),
        request.grade(),
        request.exam(),
        request.grammar(),
        request.passage(),
        request.count(),
        request.difficulty());
    return ResponseEntity.ok(new GeminiGenerateResponse(N8nProblemGenerationService.MODEL, text));
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
