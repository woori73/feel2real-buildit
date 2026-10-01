package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NewStart 지피지기 DIRECT LLM 설정. NVIDIA NIM Lightning이 primary, OpenRouter Qwen이
 * 기술적 실패 전용 fallback이다(공용 Wiki AI Router를 경유하지 않는다).
 *
 * <p>키는 환경변수 주입만 허용하고 응답·로그에 출력하지 않는다. 값 운영 최적화는 yaml/환경변수
 * override로 하고 코드를 고치지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "app.newstart.llm")
public class NewStartLlmProperties {

  /**
   * 지피지기 LLM primary. {@code wiki-router}(기본, 기존 내신Wiki AI Router=Qwen) 또는 {@code nvidia}(NVIDIA NIM Nemotron).
   * NVIDIA는 응답시간 변동(29초~180초 초과)으로 primary에서 제외했지만 비교·재사용을 위해 코드와 설정을 보존한다.
   */
  private String primary = "wiki-router";
  private final NvidiaNim nvidia = new NvidiaNim();
  private final OpenRouter openrouter = new OpenRouter();

  public String getPrimary() {
    return primary;
  }

  public void setPrimary(String value) {
    primary = value;
  }

  public NvidiaNim getNvidia() {
    return nvidia;
  }

  public OpenRouter getOpenrouter() {
    return openrouter;
  }

  public static class NvidiaNim {
    private String baseUrl = "https://integrate.api.nvidia.com/v1";
    private String model = "nvidia/nemotron-3.5-lightning-30b-a3b";
    private String apiKey = "";
    private double temperature = 1.0;
    private double topP = 0.95;
    private int maxTokens = 16384;
    private boolean enableThinking = true;
    private int reasoningBudget = 16384;
    private int timeoutSeconds = 180;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double value) { temperature = value; }
    public double getTopP() { return topP; }
    public void setTopP(double value) { topP = value; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int value) { maxTokens = value; }
    public boolean isEnableThinking() { return enableThinking; }
    public void setEnableThinking(boolean value) { enableThinking = value; }
    public int getReasoningBudget() { return reasoningBudget; }
    public void setReasoningBudget(int value) { reasoningBudget = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
  }

  public static class OpenRouter {
    private String baseUrl = "https://openrouter.ai/api/v1";
    private String model = "qwen/qwen3.7-flash";
    private String apiKey = "";
    private double temperature = 0.25;
    private int timeoutSeconds = 90;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double value) { temperature = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
  }
}
