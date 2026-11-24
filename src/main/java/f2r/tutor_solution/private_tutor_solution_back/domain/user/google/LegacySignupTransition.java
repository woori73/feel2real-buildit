package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import org.springframework.context.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@Profile("google-accounts")
public class LegacySignupTransition implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res,Object handler) throws java.io.IOException {
                if (req.getMethod().equals("GET") && req.getServletPath().equals("/register")) {
                    res.sendRedirect("/auth/google"); return false;
                }
                if (req.getMethod().equals("POST")) {
                    res.sendError(410,"신규 가입은 Google 계정을 이용해 주세요."); return false;
                }
                return true;
            }
        }).addPathPatterns("/register","/api/users/students","/api/users/tutors","/api/newstart/auth/signup");
    }
}
