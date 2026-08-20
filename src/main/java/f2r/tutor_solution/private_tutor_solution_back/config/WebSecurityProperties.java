package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 웹 보안 설정 — {@code app.security.*}.
 *
 * @param allowedOrigins 상태변경 {@code /api/**} 요청의 Origin/Referer 호스트 허용 suffix 목록.
 *                       비어있으면(local/dev/stg 기본) Origin 강제를 비활성화한다 — 브라우저 아닌
 *                       테스트/서버 호출을 막지 않기 위함. 운영(prd)만 값을 채워 CSRF 방어로 쓴다.
 *                       예: {@code feel2real.co.kr}(호스트 suffix 매칭, 서브도메인 포함).
 */
@ConfigurationProperties(prefix = "app.security")
public record WebSecurityProperties(
    List<String> allowedOrigins
) {
  public boolean enforcementEnabled() {
    return allowedOrigins != null && !allowedOrigins.isEmpty();
  }
}
