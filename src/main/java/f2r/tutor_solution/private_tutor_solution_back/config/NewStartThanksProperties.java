package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 감사회원 셀프 인증·예외 알림 설정({@code app.newstart.thanks.*}).
 *
 * <p>SMTP 비밀값(host/user/password)은 환경변수로만 주입하고 코드·Repository에 두지 않는다. 설정이 비어 있으면 예외는 ADMIN
 * 예외함에 적재되고 메일만 건너뛴다(인증 흐름은 영향받지 않는다).
 */
@Component
@ConfigurationProperties(prefix = "app.newstart.thanks")
public class NewStartThanksProperties {

  /** 자동 인증에 필요한 최소 접두부 길이. 짧은 접두부(예: 2자)는 추측하기 쉬워 예외로 보낸다. */
  private int minAutoPrefixLength = 4;
  /** 24시간 내 단순 실패 허용 횟수. 이 횟수에 도달하면 예외로 승격한다. */
  private int maxFailuresPerDay = 3;

  private String alertTo = "";
  private String alertFrom = "";
  private String smtpHost = "";
  private int smtpPort = 587;
  private String smtpUsername = "";
  private String smtpPassword = "";
  private String adminUrl = "https://feel2real.co.kr/admin/newstart/thanks-exceptions";

  public boolean mailConfigured() {
    return notBlank(alertTo) && notBlank(smtpHost) && notBlank(smtpUsername) && notBlank(smtpPassword);
  }

  private static boolean notBlank(String v) {
    return v != null && !v.isBlank();
  }

  public int getMinAutoPrefixLength() { return minAutoPrefixLength; }
  public void setMinAutoPrefixLength(int v) { minAutoPrefixLength = v; }
  public int getMaxFailuresPerDay() { return maxFailuresPerDay; }
  public void setMaxFailuresPerDay(int v) { maxFailuresPerDay = v; }
  public String getAlertTo() { return alertTo; }
  public void setAlertTo(String v) { alertTo = v; }
  public String getAlertFrom() { return alertFrom; }
  public void setAlertFrom(String v) { alertFrom = v; }
  public String getSmtpHost() { return smtpHost; }
  public void setSmtpHost(String v) { smtpHost = v; }
  public int getSmtpPort() { return smtpPort; }
  public void setSmtpPort(int v) { smtpPort = v; }
  public String getSmtpUsername() { return smtpUsername; }
  public void setSmtpUsername(String v) { smtpUsername = v; }
  public String getSmtpPassword() { return smtpPassword; }
  public void setSmtpPassword(String v) { smtpPassword = v; }
  public String getAdminUrl() { return adminUrl; }
  public void setAdminUrl(String v) { adminUrl = v; }
}
