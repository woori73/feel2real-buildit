package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.controller.AdmissionsRateLimiter;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionPgOrderService.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 학부모 공개 화면 — 로그인 없이 토큰 주소로만 연다. 스위치가 꺼져 있으면 전부 404.
 * 응답에 학생 실명·연락처를 넣지 않고, 캐시·검색 수집·리퍼러로 주소가 새지 않게 헤더를 붙인다.
 * 없는 토큰·회수·만료는 구분하지 않고 같은 404로 답한다.
 */
@Controller
public class TuitionPayPageController {

  private final TuitionBillingSwitch billingSwitch;
  private final TuitionBillLinkService linkService;
  private final TuitionPgOrderService orderService;
  private final TuitionPayRateLimiter rateLimiter;
  private final AdmissionsRateLimiter clientIpResolver;
  private final String webhookSecret;

  public TuitionPayPageController(TuitionBillingSwitch billingSwitch,
      TuitionBillLinkService linkService, TuitionPgOrderService orderService,
      TuitionPayRateLimiter rateLimiter, AdmissionsRateLimiter clientIpResolver,
      @Value("${app.payment.webhook-secret:}") String webhookSecret) {
    this.billingSwitch = billingSwitch;
    this.linkService = linkService;
    this.orderService = orderService;
    this.rateLimiter = rateLimiter;
    this.clientIpResolver = clientIpResolver;
    this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
  }

  public record NotifyRequest(String orderId, String paymentKey, Long amount) {}

  @GetMapping("/pay/{token}")
  public String bill(@PathVariable String token, Model model, HttpServletRequest request,
      HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireEnabled();
    model.addAttribute("bill", linkService.publicView(token, LocalDateTime.now())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    model.addAttribute("token", token);
    model.addAttribute("cardTest", billingSwitch.isCardTestEnabled());
    return "pay/bill";
  }

  /** 결제창(시험)으로 보낸다. 금액은 받지 않는다 — 서버의 청구 잔액으로 주문을 만든다. */
  @PostMapping("/pay/{token}/checkout")
  public String checkout(@PathVariable String token, HttpServletRequest request,
      HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireCardTestEnabled();
    return "redirect:" + orderService.createOrder(token, LocalDateTime.now())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).checkoutPath();
  }

  /** 시험 결제창 — 실제 대행사 결제창 자리에 서는 화면. 카드 정보를 받지 않는다. */
  @GetMapping("/pay/{token}/test-checkout")
  public String testCheckout(@PathVariable String token, @RequestParam String orderId,
      Model model, HttpServletRequest request, HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireCardTestEnabled();
    model.addAttribute("order", orderService.findReadyOrder(token, orderId, LocalDateTime.now())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    model.addAttribute("token", token);
    model.addAttribute("okKey", FakeTuitionPaymentGateway.OK_PREFIX + orderId);
    return "pay/checkout_test";
  }

  /** 결제창에서 돌아오는 성공 주소. 여기서 서버가 금액을 다시 확인하고 승인한다. */
  @GetMapping("/pay/{token}/success")
  public String success(@PathVariable String token, @RequestParam String orderId,
      @RequestParam String paymentKey, @RequestParam Long amount, Model model,
      HttpServletRequest request, HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireCardTestEnabled();
    Outcome outcome = orderService.confirm(token, orderId, paymentKey, amount,
        LocalDateTime.now());
    if (outcome == Outcome.NOT_FOUND) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    boolean ok = outcome == Outcome.PAID || outcome == Outcome.ALREADY_PAID;
    model.addAttribute("ok", ok);
    model.addAttribute("message", ok ? null : failureMessage(outcome));
    model.addAttribute("token", token);
    return "pay/result";
  }

  @GetMapping("/pay/{token}/fail")
  public String fail(@PathVariable String token, Model model, HttpServletRequest request,
      HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireCardTestEnabled();
    if (linkService.publicView(token, LocalDateTime.now()).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    model.addAttribute("ok", false);
    model.addAttribute("message", "결제가 완료되지 않았습니다. 다시 시도하거나 계좌 입금을 이용해 주세요.");
    model.addAttribute("token", token);
    return "pay/result";
  }

  /**
   * 대행사 결제 알림(웹훅) 자리. 환경변수 F2R_PAYMENT_WEBHOOK_SECRET이 없으면 닫혀 있다(404).
   * 같은 알림이 여러 번 와도 납부 기록은 한 번만 한다.
   */
  @PostMapping("/pay/notify")
  @ResponseBody
  public ResponseEntity<Map<String, String>> notify(
      @RequestHeader(value = "X-F2R-Webhook-Secret", required = false) String secret,
      @RequestBody NotifyRequest body, HttpServletRequest request, HttpServletResponse response) {
    guard(request, response);
    billingSwitch.requireCardTestEnabled();
    if (webhookSecret.isBlank() || secret == null || !MessageDigest.isEqual(
        webhookSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Outcome outcome = orderService.confirm(null, body.orderId(), body.paymentKey(),
        body.amount(), LocalDateTime.now());
    return ResponseEntity.ok(Map.of("result", outcome.name()));
  }

  private void guard(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
    if (!rateLimiter.tryAcquire(clientIpResolver.resolveClientIp(request))) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    }
  }

  private String failureMessage(Outcome outcome) {
    return switch (outcome) {
      case AMOUNT_MISMATCH -> "결제 금액이 청구 금액과 달라 승인하지 않았습니다.";
      case BILL_CHANGED -> "청구 내용이 바뀌었거나 이미 납부되었습니다. 청구 화면을 다시 확인해 주세요.";
      default -> "결제가 승인되지 않았습니다. 다시 시도하거나 계좌 입금을 이용해 주세요.";
    };
  }
}
