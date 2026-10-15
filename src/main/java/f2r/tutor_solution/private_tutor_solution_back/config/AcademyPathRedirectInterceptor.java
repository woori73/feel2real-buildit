package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 경로형 공개 페이지({@code /academies/{slug}}) 진입 시, 해당 Academy가 handle을 가지면
 * 서브도메인({@code https://{handle}.{base-domain}/})으로 301 영구 이동한다.
 *
 * <p>{@code /academies/{slug}}는 컨트롤러 없는 ViewController라 인터셉터가 리다이렉트 지점이 된다.
 * base-domain 미설정(local/test)이면 no-op. handle 없는 Academy(전환기)는 통과해 경로형 유지.
 * 서브도메인 필터의 내부 forward가 다시 걸리지 않도록 REQUEST dispatch에서만 동작한다.
 */
@Component
public class AcademyPathRedirectInterceptor implements HandlerInterceptor {

  // ObjectProvider 지연 주입: @WebMvcTest 슬라이스(JPA 레포 없음)에서도 컨텍스트가 뜨도록.
  // base-domain 미설정이면 preHandle이 early return이라 레포를 아예 조회하지 않는다.
  private final ObjectProvider<AcademyRepository> academyRepository;
  private final PublicSiteProperties props;

  public AcademyPathRedirectInterceptor(
      ObjectProvider<AcademyRepository> academyRepository, PublicSiteProperties props) {
    this.academyRepository = academyRepository;
    this.props = props;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!props.hasBaseDomain() || request.getDispatcherType() != DispatcherType.REQUEST) {
      return true;
    }
    String slug = lastPathSegment(request.getRequestURI());
    if (slug == null) return true;

    return academyRepository.getObject().findBySlug(slug)
        .map(Academy::getHandle)
        .filter(handle -> handle != null && !handle.isBlank())
        .map(handle -> {
          response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
          response.setHeader("Location", "https://" + handle + "." + props.baseDomain() + "/");
          return false;
        })
        .orElse(true);
  }

  // getRequestURI()는 미디코드 상태 — 한글 slug는 percent-encoded로 도착하므로
  // 디코드해야 findBySlug와 일치한다(academy_public.js도 decodeURIComponent로 동일 처리).
  private String lastPathSegment(String uri) {
    if (uri == null) return null;
    int slash = uri.lastIndexOf('/');
    if (slash < 0 || slash == uri.length() - 1) return null;
    return java.net.URLDecoder.decode(uri.substring(slash + 1), java.nio.charset.StandardCharsets.UTF_8);
  }
}
