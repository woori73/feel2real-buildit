package f2r.tutor_solution.private_tutor_solution_back.domain.sns.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인스타 자동 피드 설정(app.sns.*). 비밀값(토큰 암호화 키·GPU 작업자 토큰·Meta 앱 비밀)은 환경변수로만 받는다.
 * 월 상한이 비었거나 0이면 생성하지 않는다(HANDOFF §8).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.sns")
public class SnsProperties {

  /** 매분 예약 확인·예약 발행·토큰 갱신 스케줄러를 돌릴지. */
  private boolean schedulerEnabled = false;

  /** 전체 월 상한(원). null·0이면 생성 차단. */
  private Integer monthlyBudgetKrw;

  /** GPU 1시간 요금(원). runyour.ai RTX A5000 = 1,261원. */
  private int gpuKrwPerHour = 1261;

  /** 상한 판단에 쓰는 이미지 1장 예상 GPU 시간(초). 실제 비용은 작업자가 보낸 걸린 시간으로 기록한다. */
  private int gpuEstimatedSecondsPerImage = 120;

  /** 문구 1회 생성 비용(원). 현재 문구 모델 비용이 따로 청구되지 않으면 0. */
  private int textCostKrwPerCall = 0;

  /** GPU 작업자 인증 토큰(SNS_WORKER_TOKEN). 비면 작업자 API는 503. */
  private String workerToken;

  /** 토큰 암호화 키(32바이트 base64, SNS_TOKEN_KEY_BASE64). 비면 계정 연결 불가. */
  private String tokenKeyBase64;

  /** 인스타그램이 이미지를 가져갈 공개 주소의 기준(https://feel2real.co.kr). */
  private String publicBaseUrl = "https://feel2real.co.kr";

  private Meta meta = new Meta();

  @Getter
  @Setter
  public static class Meta {
    /** Instagram API with Instagram Login 앱 ID / 비밀(Meta 개발자 앱 설정). */
    private String appId;
    private String appSecret;
    /** OAuth 돌아올 주소. 비면 publicBaseUrl + /admin/sns/accounts/callback. */
    private String redirectUri;
    private String graphBaseUrl = "https://graph.instagram.com";
    private String graphVersion = "v23.0";
    private String authorizeUrl = "https://www.instagram.com/oauth/authorize";
    private String tokenUrl = "https://api.instagram.com/oauth/access_token";
    private int timeoutSeconds = 30;
  }

  public boolean budgetConfigured() {
    return monthlyBudgetKrw != null && monthlyBudgetKrw > 0;
  }

  public boolean metaConfigured() {
    return notBlank(meta.appId) && notBlank(meta.appSecret);
  }

  public String effectiveRedirectUri() {
    return notBlank(meta.redirectUri) ? meta.redirectUri : trimSlash(publicBaseUrl) + "/admin/sns/accounts/callback";
  }

  public static String trimSlash(String s) {
    return s == null ? "" : s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
