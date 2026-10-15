package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozStep;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozStep.Step;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart AI 질문 API.
 *
 * <p>비로그인은 SecurityConfig의 {@code /api/**} catch-all이 401로 막고, 로그인 회원은
 * {@link NewStartEntitlementService}의 THANKS_MEMBER 판정에서 403/통과가 갈린다. 지피지기는
 * 운영 비용이 드는 실행형 기능이므로 "로그인 회원 = 전원 실행"으로 두지 않는다.
 * 거절 응답은 {@link NewStartEntitlementExceptionHandler}가 안내 문구로 번역한다.
 *
 * <p>에러 상세(provider 응답, exception, stack trace)는 브라우저로 노출하지 않는다 — service가
 * 실패를 result-object로 변환하고 controller는 안내 문구만 내린다.
 */
@RestController
@RequestMapping("/api/newstart/ai")
public class NewStartAiChatController {

  private static final Logger log = LoggerFactory.getLogger(NewStartAiChatController.class);

  static final String STAGE_CASE = "CASE";
  static final String STAGE_OFFICIAL = "OFFICIAL";

  private final NewStartAiChatService newStartAiChatService;
  private final NewStartEntitlementService newStartEntitlementService;

  public NewStartAiChatController(NewStartAiChatService newStartAiChatService,
      NewStartEntitlementService newStartEntitlementService) {
    this.newStartAiChatService = newStartAiChatService;
    this.newStartEntitlementService = newStartEntitlementService;
  }

  /** STEP 1 — 전자책 + 실제 사례 기반 1차 답변. 공식 자료 조사를 기다리지 않고 먼저 응답한다. */
  @PostMapping("/chat")
  public ResponseEntity<ChatResponse> chat(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody ChatRequest request) {
    long t0 = System.nanoTime();
    newStartEntitlementService.requireThanksMember(principal);
    long tAuth = System.nanoTime();
    NewStartAiChatService.NewStartAiAnswer answer =
        newStartAiChatService.ask(request == null ? null : request.message());
    long tService = System.nanoTime();
    // 구간 실측: AUTH는 grant 판정, HANDLER는 controller+service(직렬화 제외). 직렬화·NETWORK는
    // 클라이언트 time_total에서 HANDLER를 뺀 값으로 계산한다(service/transport 로그와 함께 본다).
    log.info("newstart ai chat endpoint authMs={} serviceMs={} handlerMs={} status={} sourceCount={} answerChars={}",
        ms(tAuth - t0), ms(tService - tAuth), ms(tService - t0), answer.ok() ? "ok" : "error",
        answer.sources().size(), answer.answer().length());
    if (!answer.ok()) {
      return ResponseEntity.badRequest().body(ChatResponse.failure(answer.errorMessage(), toSourceDtos(answer.sources())));
    }
    ChatResponse base = ChatResponse.success(answer.answer(), answer.model(), toSourceDtos(answer.sources()), STAGE_CASE);
    // §7 — 질문을 결정적 키워드로 A to Z 단계에 연결한 이동 링크는 ADMIN에게만 내려준다.
    // A to Z 화면은 ADMIN 전용이라 감사회원에게 내려주면 죽은 링크가 된다(2026-10 정책).
    if (!"ADMIN".equals(principal.role())) {
      return ResponseEntity.ok(base);
    }
    return ResponseEntity.ok(withAtozStep(base, request == null ? null : request.message()));
  }

  /**
   * STEP 2 — 1차 답변이 표시된 뒤 별도 호출로 현재 공식자료를 확인·보완한다. {@code step1Answer}는 비교 입력일 뿐이며
   * 이 API는 1차 답변을 수정·재생성하지 않는다. 실패·timeout은 이 단계만의 실패다.
   */
  @PostMapping("/chat/official")
  public ResponseEntity<ChatResponse> chatOfficial(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody OfficialRequest request) {
    long t0 = System.nanoTime();
    newStartEntitlementService.requireThanksMember(principal);
    NewStartAiChatService.NewStartAiAnswer answer = newStartAiChatService.askOfficial(
        request == null ? null : request.message(), request == null ? null : request.step1Answer());
    log.info("newstart ai chat official endpoint handlerMs={} status={} sourceCount={} answerChars={}",
        ms(System.nanoTime() - t0), answer.ok() ? "ok" : "error", answer.sources().size(), answer.answer().length());
    if (!answer.ok()) {
      return ResponseEntity.badRequest().body(ChatResponse.failure(answer.errorMessage(), toSourceDtos(answer.sources())));
    }
    return ResponseEntity.ok(ChatResponse.success(answer.answer(), answer.model(), toSourceDtos(answer.sources()), STAGE_OFFICIAL));
  }

  private static long ms(long nanos) {
    return Math.round(nanos / 1_000_000.0);
  }

  private static java.util.List<SourceDto> toSourceDtos(java.util.List<NewStartAiChatService.AnswerSource> sources) {
    return sources.stream().map(s -> new SourceDto(s.kind(), s.title(), s.url())).toList();
  }

  /** 질문에서 A to Z 단계를 결정적으로 찾아 STEP 1 응답에 이동 링크를 얹는다(신호 없으면 그대로 반환). */
  private static ChatResponse withAtozStep(ChatResponse base, String message) {
    int no = NewStartAtozStep.detectStepNo(message);
    if (no < 0) return base;
    Step step = NewStartAtozStep.byNo(no);
    if (step == null) return base;
    return new ChatResponse(base.status(), base.answer(), base.errorMessage(), base.model(),
        base.sources(), base.stage(), step.no(), step.title(), step.url());
  }

  /** @param message 사용자가 입력한 질문. 빈 값/길이 초과는 service가 검증한다. */
  public record ChatRequest(String message) {}

  /** @param message 원 질문. @param step1Answer 이미 표시된 1차 답변(비교 입력, 수정 대상 아님 — 서버가 길이만 제한). */
  public record OfficialRequest(String message, String step1Answer) {}

  /** 답변 근거 출처. kind는 "공식 자료" / "부실차주 카페 사례" 등 출처 성격 라벨 — 커뮤니티 근거를 공식처럼 표시하지 않기 위한 구분. */
  public record SourceDto(String kind, String title, String url) {}

  /**
   * @param status "ok" | "error". @param answer 성공 시 AI 답변, 실패 시 빈 문자열.
   * @param errorMessage 실패 시 사용자용 안내 문구(내부 예외·provider 상세 미포함), 성공 시 null.
   * @param model Wiki Router가 반환한 모델 식별자(검증용). UI 표시는 선행이며 실패 시 null.
   * @param sources 답변 조립에 사용된 근거 출처 목록(빈 배열 가능).
   * @param stage "CASE"(1차: 전자책+실제 사례) | "OFFICIAL"(2차: 현재 규정 확인). 실패 응답은 null.
   * @param atozStepNo/atozStepTitle/atozStepUrl §7 결정적 A to Z 단계 연결(없으면 모두 null).
   */
  public record ChatResponse(String status, String answer, String errorMessage, String model,
                             java.util.List<SourceDto> sources, String stage,
                             Integer atozStepNo, String atozStepTitle, String atozStepUrl) {
    /** @param stage "CASE"(1차: 전자책+실제 사례) | "OFFICIAL"(2차: 현재 규정 확인). 실패 응답은 null. */
    public static ChatResponse success(String answer, String model, java.util.List<SourceDto> sources, String stage) {
      return new ChatResponse("ok", answer, null, model, sources, stage, null, null, null);
    }

    public static ChatResponse failure(String errorMessage, java.util.List<SourceDto> sources) {
      return new ChatResponse("error", "", errorMessage, null, sources, null, null, null, null);
    }
  }
}
