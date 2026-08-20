package f2r.tutor_solution.private_tutor_solution_back.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 사이트 설정 — {@code app.public.*}.
 *
 * @param baseDomain 서브도메인 base(예: prd={@code feel2real.co.kr}). 공란이면 서브도메인 라우팅/301 비활성
 *                   (로컬·테스트 기본). {@code {handle}.{baseDomain}} Host를 academy로 해석한다.
 */
@ConfigurationProperties(prefix = "app.public")
public record PublicSiteProperties(
    String baseDomain
) {
  public boolean hasBaseDomain() {
    return baseDomain != null && !baseDomain.isBlank();
  }
}
