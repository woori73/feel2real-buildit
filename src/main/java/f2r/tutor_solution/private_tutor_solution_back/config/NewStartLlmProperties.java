package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NewStart 지피지기 DIRECT LLM 설정. primary는 {@code app.newstart.llm.primary}로 고른다:
 * {@code wiki-router}(기본, 기존 내신Wiki AI Router=Qwen),
 * {@code meta}(Meta Muse Spark DIRECT),
 * {@code nvidia}(NVIDIA NIM Nemotron, 보존).
 * fallback은 OpenRouter Qwen DIRECT(앱 자신의 OPENROUTER_API_KEY가 있을 때만 동작).
 *
 * <p>키는 환경변수 주입만 허용하고 응답·로그에 출력하지 않는다. 값 운영 최적화는 yaml/환경변수
 * override로 하고 코드를 고치지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "app.newstart.llm")
public class NewStartLlmProperties {

  /**
   * 지피지기 LLM primary. {@code wiki-router}(기본, 기존 내신Wiki AI Router=Qwen),
   * {@code meta}(Meta Muse Spark DIRECT),
   * {@code nvidia}(NVIDIA NIM Nemotron, 보존).
   * 2026-10-01: NVIDIA 응답시간이 29초~180초 초과로 변동해 primary에서 제외. Router 설정·키는 건드리지 않는다.
   * 2026-10-05: Meta Muse Spark(muse-spark-1.3-contributor) primary 옵션 추가.
   */
  private String primary = "wiki-router";
  private final NvidiaNim nvidia = new NvidiaNim();
  private final OpenRouter openrouter = new OpenRouter();
  private final MetaMuseSpark meta = new MetaMuseSpark();

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

  public MetaMuseSpark getMeta() {
    return meta;
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

  /**
   * Meta Muse Spark DIRECT 설정 (지피지기 답변 생성용).
   * Vision의 {@code app.newstart.vision}(META_VISION_API_KEY)과 분리된 독립 설정이다.
   * muse-spark-1.3-contributor는 reasoning 모델이므로 maxTokens는 8000 이상을 유지해야 한다
   * (낮으면 reasoning 토큰에 소진되어 content가 null로 반환됨).
   */
  public static class MetaMuseSpark {
    private String baseUrl = "https://api.meta.ai/v1";
    private String model = "muse-spark-1.3-contributor";
    private String apiKey = "";
    private double temperature = 0.25;
    private int maxTokens = 8000;
    private int timeoutSeconds = 120;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double value) { temperature = value; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int value) { maxTokens = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
  }
}
