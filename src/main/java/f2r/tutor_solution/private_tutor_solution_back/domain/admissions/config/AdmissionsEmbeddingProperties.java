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
    private int timeoutMs = 1500;
    /** 재색인(문서 임베딩) 1건 타임아웃(ms). 질문용 timeoutMs와 분리한다. EC2 t3.large 실측(합성 문서 6회): 117자 평균 1.3s, 1900자 평균 2.9s·최대 3.0s → 약 7배 여유인 20s. */
    private int indexTimeoutMs = 20000;
    /** 재색인 1건의 재시도 횟수(첫 시도 제외). 503(서버 사용 중)·타임아웃·연결 오류만 재시도한다. */
    private int indexRetries = 2;
    /** 재시도 사이 대기(ms). 시도마다 배수로 늘린다. */
    private long indexRetryBackoffMs = 2000;
    /** 재색인 항목 사이 쉬는 시간(ms). 동시성 1인 임베딩 서버에 질문 임베딩이 끼어들 틈을 준다. */
    private long indexPauseMs = 200;
    private int vectorLimit = 20;
    /** 키워드 일치가 없는 항목이 근거가 되려면 필요한 최소 코사인 유사도. 실제 모델 값은 운영 전 보정한다. */
    private double minSimilarity = 0.5;
    /** 연속 실패 횟수가 이 값에 이르면 서킷을 연다(임베딩 호출을 건너뛰고 키워드 검색으로 전환). */
    private int circuitFailureThreshold = 2;
    /** 서킷이 열려 있는 시간(ms). */
    private long circuitOpenMs = 30000;
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
    public int getIndexTimeoutMs() { return indexTimeoutMs; }
    public void setIndexTimeoutMs(int value) { indexTimeoutMs = value; }
    public int getIndexRetries() { return indexRetries; }
    public void setIndexRetries(int value) { indexRetries = value; }
    public long getIndexRetryBackoffMs() { return indexRetryBackoffMs; }
    public void setIndexRetryBackoffMs(long value) { indexRetryBackoffMs = value; }
    public long getIndexPauseMs() { return indexPauseMs; }
    public void setIndexPauseMs(long value) { indexPauseMs = value; }
    public double getMinSimilarity() { return minSimilarity; }
    public void setMinSimilarity(double value) { minSimilarity = value; }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int value) { circuitFailureThreshold = value; }
    public long getCircuitOpenMs() { return circuitOpenMs; }
    public void setCircuitOpenMs(long value) { circuitOpenMs = value; }
    public int getVectorLimit() { return vectorLimit; }
    public void setVectorLimit(int value) { vectorLimit = value; }
    public double getLikeWeight() { return likeWeight; }
    public void setLikeWeight(double value) { likeWeight = value; }
    public double getVectorWeight() { return vectorWeight; }
    public void setVectorWeight(double value) { vectorWeight = value; }
    public double getOfficialBonus() { return officialBonus; }
    public void setOfficialBonus(double value) { officialBonus = value; }
}
