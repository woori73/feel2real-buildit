package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 입시상담 가이드 LLM 설정. {@code app.admissions.llm.primary}로 primary를 고른다:
 * {@code wiki-router}(보존), {@code meta}(기본, Meta Muse Spark DIRECT), {@code nvidia}(NVIDIA NIM, 보존).
 *
 * <p>transport 빈은 NewStart 지피지기와 공유한다 (인프라 재사용).
 * primary 선택만 입시상담 전용 설정으로 분리한다.
 * 키는 환경변수 주입만 허용하고 응답·로그에 출력하지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "app.admissions.llm")
public class AdmissionsLlmProperties {

    /**
     * 입시상담 LLM primary. {@code wiki-router}(보존),
     * {@code meta}(기본, Meta Muse Spark DIRECT),
     * {@code nvidia}(NVIDIA NIM Nemotron, 보존).
     */
    private String primary = "meta";

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String value) {
        primary = value;
    }
}
