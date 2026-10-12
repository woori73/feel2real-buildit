package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 에러 페이지 처리 컨트롤러.
 *
 * <p>2026-10-12 변경: 존재하지 않는 페이지(404)를 /home으로 리다이렉트하지 않고
 * 정상적인 404 화면을 표시하도록 변경. 401(로그인 필요), 403(권한 부족), 404(페이지 없음)를 구분한다.
 * NewStart 전용 로그인 흐름(/newstart/**)은 기존대로 유지된다.
 */
@Controller
public class PageNotFoundRedirectController implements ErrorController {

    private static final String[] NON_PAGE_PREFIXES = {
            "/css/",
            "/js/",
            "/img/",
            "/swagger-ui/",
            "/v3/api-docs/",
            "/actuator/"
    };

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        int status = resolveStatus(request);
        String uri = originalUri(request);

        // 404: 페이지 없음 — /home으로 리다이렉트하지 않고 404 화면 표시
        if (status == HttpStatus.NOT_FOUND.value()
                && isGetOrHead(request)
                && isGeneralPageRoute(uri)) {
            ModelAndView mav = new ModelAndView("error/404");
            mav.setStatus(HttpStatus.NOT_FOUND);
            mav.addObject("path", uri);
            return mav;
        }

        // 401: 로그인 필요 — JSON API는 401 그대로, HTML 페이지는 로그인 안내
        // (SecurityConfig의 defaultAuthenticationEntryPointFor에서 이미 처리됨)
        // 403: 권한 부족 — 403 그대로 (프론트에서 안내 표시)

        return ResponseEntity.status(status).build();
    }

    private int resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Number number) {
            return number.intValue();
        }
        if (status instanceof String statusText) {
            try {
                return Integer.parseInt(statusText);
            } catch (NumberFormatException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private boolean isGetOrHead(HttpServletRequest request) {
        Object errorMethod = request.getAttribute(RequestDispatcher.ERROR_METHOD);
        String method = errorMethod instanceof String value ? value : request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private String originalUri(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uri instanceof String value ? value : null;
    }

    private boolean isGeneralPageRoute(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        String path = uri.split("[?#]", 2)[0];
        if (!path.startsWith("/")) {
            return false;
        }
        if (path.equals("/api") || path.startsWith("/api/")) {
            return false;
        }
        for (String prefix : NON_PAGE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        if (path.equals("/favicon.ico")
                || path.equals("/robots.txt")
                || path.equals("/sitemap.xml")) {
            return false;
        }

        return !lastSegmentHasFileExtension(path);
    }

    private boolean lastSegmentHasFileExtension(String path) {
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int lastDot = lastSegment.lastIndexOf('.');
        return lastDot > 0 && lastDot < lastSegment.length() - 1;
    }
}
