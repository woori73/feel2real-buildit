package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보존 기능 라우팅 설정 — {@code app.archived.*}.
 *
 * <p>{@code /archived/**}는 초기 REQUEST에서 canonical target path로 바뀌고 기존 핸들러를
 * 그대로 사용한다. 상용 화면에서 빠진 기능을 운영자가 내부 확인할 수 있게 남기는 용도다.</p>
 *
 * @param enabled {@code /archived/**} alias 전체 활성 여부
 * @param requireAdmin true면 ROLE_ADMIN만 허용, false면 로그인 사용자 전체 허용
 */
@ConfigurationProperties(prefix = "app.archived")
public record ArchivedRoutesProperties(
    boolean enabled,
    boolean requireAdmin
) {
}
