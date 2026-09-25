package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart AI 질문 API.
 *
 * <p>인증 정책은 SecurityConfig의 기존 {@code /api/**} catch-all(인증 필요)을 그대로 따른다 —
 * Wiki AI Router가 로그인 principal 기준 X-BuildIt-* 서명을 요구하므로 익명 질문은 어차피
 * 불가능하다. 이 계층에서 새로운 권한 규칙을 만들지 않는다.
 *
 * <p>에러 상세(provider 응답, exception, stack trace)는 브라우저로 노출하지 않는다 — service가
 * 실패를 result-object로 변환하고 controller는 안내 문구만 내린다.
 */
@RestController
@RequestMapping("/api/newstart/ai")
public class NewStartAiChatController {

  private final NewStartAiChatService newStartAiChatService;

  public NewStartAiChatController(NewStartAiChatService newStartAiChatService) {
    this.newStartAiChatService = newStartAiChatService;
  }

  @PostMapping("/chat")
  public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    NewStartAiChatService.NewStartAiAnswer answer =
        newStartAiChatService.ask(request == null ? null : request.message());
    if (!answer.ok()) {
      return ResponseEntity.badRequest().body(ChatResponse.failure(answer.errorMessage(), toSourceDtos(answer.sources())));
    }
    return ResponseEntity.ok(ChatResponse.success(answer.answer(), answer.model(), toSourceDtos(answer.sources())));
  }

  private static java.util.List<SourceDto> toSourceDtos(java.util.List<NewStartAiChatService.AnswerSource> sources) {
    return sources.stream().map(s -> new SourceDto(s.kind(), s.title(), s.url())).toList();
  }

  /** @param message 사용자가 입력한 질문. 빈 값/길이 초과는 service가 검증한다. */
  public record ChatRequest(String message) {}

  /** 답변 근거 출처. kind는 "공식 자료" / "부실차주 카페 사례" 등 출처 성격 라벨 — 커뮤니티 근거를 공식처럼 표시하지 않기 위한 구분. */
  public record SourceDto(String kind, String title, String url) {}

  /**
   * @param status "ok" | "error". @param answer 성공 시 AI 답변, 실패 시 빈 문자열.
   * @param errorMessage 실패 시 사용자용 안내 문구(내부 예외·provider 상세 미포함), 성공 시 null.
   * @param model Wiki Router가 반환한 모델 식별자(검증용). UI 표시는 선행이며 실패 시 null.
   * @param sources 답변 조립에 사용된 근거 출처 목록(빈 배열 가능).
   */
  public record ChatResponse(String status, String answer, String errorMessage, String model,
                             java.util.List<SourceDto> sources) {
    public static ChatResponse success(String answer, String model, java.util.List<SourceDto> sources) {
      return new ChatResponse("ok", answer, null, model, sources);
    }

    public static ChatResponse failure(String errorMessage, java.util.List<SourceDto> sources) {
      return new ChatResponse("error", "", errorMessage, null, sources);
    }
  }
}
