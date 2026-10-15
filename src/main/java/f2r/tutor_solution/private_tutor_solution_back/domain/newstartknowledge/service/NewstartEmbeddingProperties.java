package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EmbeddingGemma 벡터 검색 설정.
 * enabled=false이면 기존 LIKE 검색만 사용 (안전장치).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "newstart.embedding")
public class NewstartEmbeddingProperties {

    /** 벡터 검색 활성화. false이면 LIKE 전용 (기본값). */
    private boolean enabled = false;

    /** 임베딩 서비스 URL (예: http://127.0.0.1:8001). */
    private String serviceUrl = "";

    /** 쿼리 임베딩 HTTP 타임아웃 (ms). */
    private int timeoutMs = 5000;

    /** 하이브리드 가중치: LIKE. */
    private double likeWeight = 0.3;

    /** 하이브리드 가중치: 벡터. */
    private double vectorWeight = 0.7;

    /** 공식 규정 가산점. */
    private double officialBonus = 0.1;

    /** 벡터 후보 수. */
    private int vectorLimit = 20;

    /** 임베딩 모델 버전 (벡터 테이블 필터). */
    private String modelVersion = "embeddinggemma-2";

    /** 벡터 차원. */
    private int dimension = 768;
}
