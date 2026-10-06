package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wemade 도메인용 Jackson ObjectMapper 명시적 Bean 정의.
 *
 * 배경:
 * - WemadeKisClient는 생성자로 ObjectMapper를 주입받는다.
 * - 기존에는 Spring Boot의 JacksonAutoConfiguration에만 의존했으나,
 *   특정 조건(@ConditionalOnClass 등)에서 auto-configuration이 스킵되면
 *   "No qualifying bean of type ObjectMapper"로 시작 실패가 발생했다.
 * - 이 설정은 auto-configuration과 무관하게 ObjectMapper 빈을 명시적으로 제공하여
 *   시작 안정성을 보장한다.
 *
 * JacksonAutoConfiguration은 @ConditionalOnMissingBean이므로,
 * 이 빈이 존재하면 auto-configuration의 ObjectMapper 생성을 건너뛴다 (충돌 없음).
 */
@Configuration
public class WemadeJacksonConfig {

    @Bean
    @Primary
    public ObjectMapper wemadeObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // KIS API 응답 처리에 필요한 기본 설정
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
