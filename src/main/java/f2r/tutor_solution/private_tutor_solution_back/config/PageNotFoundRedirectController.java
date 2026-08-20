package f2r.tutor_solution.private_tutor_solution_back.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

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

        if (status == HttpStatus.NOT_FOUND.value()
                && isGetOrHead(request)
                && isGeneralPageRoute(originalUri(request))) {
            return new ModelAndView("redirect:/home");
        }

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
