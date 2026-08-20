package f2r.tutor_solution.private_tutor_solution_back.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 — application-*.yaml의 {@code app.jwt.*}와 매핑된다.
 *
 * <p>운영(prd)에서는 {@code JWT_SECRET} 환경변수로 secret을 주입한다. 로컬은 yaml의 default 값을 그대로 사용해도 무방.
 *
 * @param secret HS256 서명용 비밀키. 최소 32 byte 이상이어야 함 (HMAC-SHA256 요구사항).
 * @param expirationSeconds Access token 만료 (초). 기본 24h.
 * @param cookieName 쿠키 이름. 운영/로컬 동일.
 * @param secureCookie HTTPS 환경에서 {@code Secure} 플래그 부여 여부. 운영=true, 로컬=false.
 * @param cookieDomain 쿠키 {@code Domain} 스코프. 운영(prd)만 {@code .feel2real.co.kr}(서브도메인 공유),
 *                     로컬/dev/stg는 공란/null(host-only). 빈 값이면 CookieUtil이 {@code Domain}을 아예 붙이지 않는다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationSeconds,
        String cookieName,
        boolean secureCookie,
        String cookieDomain
) {
}
