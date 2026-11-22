package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 수강료 청구 링크·입금 대조 스위치. 기본 OFF(app.payment.enabled=false)이며 운영에서는 켜지 않는다.
 * OFF면 새 주소는 모두 404다. 결제창(시험)은 app.payment.card-test까지 켜야 보인다.
 */
@Component
public class TuitionBillingSwitch {

  private final boolean enabled;
  private final boolean cardTest;

  public TuitionBillingSwitch(
      @Value("${app.payment.enabled:false}") boolean enabled,
      @Value("${app.payment.card-test:false}") boolean cardTest) {
    this.enabled = enabled;
    this.cardTest = cardTest;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** 결제창 시험 흐름은 전체 스위치와 card-test가 모두 켜져야 열린다. */
  public boolean isCardTestEnabled() {
    return enabled && cardTest;
  }

  public void requireEnabled() {
    if (!enabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  public void requireCardTestEnabled() {
    if (!isCardTestEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }
}
