package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway migrate 전 repair를 수행한다.
 *
 * 실패로 기록된 migration이 schema history에 남아 있으면 이후 모든 기동이
 * "Detected failed migration ... run repair" 로 막힌다. repair()는 실패 기록만
 * 제거하고 성공 기록에는 손대지 않으므로 migrate()와 함께 호출하면 안전하게 복구된다.
 *
 * 배경(2026-10-06): V1038__add_wemade_exchange.sql의 MySQL 미지원 구문
 * (ADD COLUMN IF NOT EXISTS)으로 마이그레이션이 실패했고, 실패 기록이 남아
 * 수정된 V1038도 실행되지 못해 배포가 롤백됐다.
 */
@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
