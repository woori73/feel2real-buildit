package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 입시상담 가이드 임베딩 설정.
 * NewStart의 NewstartEmbeddingProperties 패턴을 입시 전용으로 적용.
 */
@Component
@ConfigurationProperties(prefix = "app.admissions.embedding")
public class AdmissionsEmbeddingProperties {

    private boolean enabled = true;
    private String serviceUrl = "";
    private String modelVersion = "embeddinggemma-2";
    private int dimension = 768;
    private int timeoutMs = 10000;
    private int vectorLimit = 20;
    private double likeWeight = 0.4;
    private double vectorWeight = 0.6;
    private double officialBonus = 0.1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String value) { serviceUrl = value; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String value) { modelVersion = value; }
    public int getDimension() { return dimension; }
    public void setDimension(int value) { dimension = value; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int value) { timeoutMs = value; }
    public int getVectorLimit() { return vectorLimit; }
    public void setVectorLimit(int value) { vectorLimit = value; }
    public double getLikeWeight() { return likeWeight; }
    public void setLikeWeight(double value) { likeWeight = value; }
    public double getVectorWeight() { return vectorWeight; }
    public void setVectorWeight(double value) { vectorWeight = value; }
    public double getOfficialBonus() { return officialBonus; }
    public void setOfficialBonus(double value) { officialBonus = value; }
}
