package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.statistics.alb")
public class AlbAccessStatisticsProperties {
  private boolean enabled;
  private String bucket;
  private String keyPrefix = "";
  private String region = "ap-northeast-2";
  private String timeZone = "Asia/Seoul";
  private long pollDelayMs = 300_000;
  private int maxObjectsPerPoll = 100;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getBucket() { return bucket; }
  public void setBucket(String bucket) { this.bucket = bucket; }
  public String getKeyPrefix() { return keyPrefix; }
  public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getTimeZone() { return timeZone; }
  public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
  public long getPollDelayMs() { return pollDelayMs; }
  public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }
  public int getMaxObjectsPerPoll() { return maxObjectsPerPoll; }
  public void setMaxObjectsPerPoll(int maxObjectsPerPoll) { this.maxObjectsPerPoll = maxObjectsPerPoll; }
}
