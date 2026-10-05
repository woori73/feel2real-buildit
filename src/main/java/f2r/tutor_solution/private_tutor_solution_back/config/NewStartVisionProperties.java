package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NewStart 채무 이미지 Vision 전용 설정. Meta Muse Spark API를 사용한다.
 *
 * <p>지피지기 Agent의 {@link NewStartLlmProperties}와는 분리된 독립 설정이다.
 * 키는 환경변수 주입만 허용하고 응답·로그에 출력하지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "app.newstart.vision")
public class NewStartVisionProperties {

  private String baseUrl = "https://api.meta.ai/v1";
  private String model = "muse-spark-1.3-contributor";
  private String apiKey = "";
  private double temperature = 0.1;
  private int maxTokens = 8000;
  private int timeoutSeconds = 120;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String value) {
    baseUrl = value;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String value) {
    model = value;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String value) {
    apiKey = value;
  }

  public double getTemperature() {
    return temperature;
  }

  public void setTemperature(double value) {
    temperature = value;
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(int value) {
    maxTokens = value;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int value) {
    timeoutSeconds = value;
  }
}
