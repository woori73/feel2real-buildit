package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.LunaTransformRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.dto.LunaTransformResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.LunaProblemTransformationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemGeneratorLunaController {
  private final LunaProblemTransformationService service;

  public ProblemGeneratorLunaController(LunaProblemTransformationService service) {
    this.service = service;
  }

  @PostMapping("/api/problem-generator/luna/transform")
  public ResponseEntity<LunaTransformResponse> transform(@RequestBody LunaTransformRequest request) {
    String text = service.transform(request.sourceQuestion(), request.instructions(), request.difficulty(), request.digitalTwinContext());
    return ResponseEntity.ok(new LunaTransformResponse(LunaProblemTransformationService.MODEL, text));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("detail", exception.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> upstreamError(IllegalStateException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", exception.getMessage()));
  }
}
