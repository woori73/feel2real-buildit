package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NoOpOAuth2AuthorizedClientRepository;

@Configuration
@Profile("google-accounts")
public class GoogleLoginSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain googleLoginChain(HttpSecurity http, GoogleEnrollment enrollment, GoogleJoinController join) throws Exception {
        http.securityMatcher("/oauth2/authorization/google","/oauth2/authorization/google-student",
                "/login/oauth2/code/google","/login/oauth2/code/google-student")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .securityContext(ctx -> ctx.securityContextRepository(new NullSecurityContextRepository()))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
                        throws ServletException,IOException {
                    var session=req.getSession(false);
                    Object pending=session==null?null:session.getAttribute(GoogleJoinController.FLOW);
                    if (!(pending instanceof GoogleJoinController.Flow f) || f.expires().isBefore(java.time.Instant.now())
                        || f.kind().equals("entry") || req.getRequestURI().endsWith("google-student") !=
                              (f.kind().equals("login") || f.kind().equals("student"))) {
                        res.sendRedirect("/auth/google"); return;
                    }
                    chain.doFilter(req,res);
                }
            },OAuth2AuthorizationRequestRedirectFilter.class)
            .oauth2Login(o -> o.authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
                .userInfoEndpoint(info -> info.oidcUserService(req -> new DefaultOidcUser(List.of(),req.getIdToken())))
                .successHandler((req,res,auth) -> {
                    var user=(OidcUser)((OAuth2AuthenticationToken)auth).getPrincipal();
                    var flow=GoogleJoinController.flow(req);
                    String email=user.getEmail();
                    if(!Boolean.TRUE.equals(user.getEmailVerified()) || email==null || email.length()>100 ||
                        user.getSubject()==null || user.getSubject().length()>64) {
                        req.getSession().invalidate(); res.sendError(403); return;
                    }
                    var found=enrollment.linked(user.getSubject());
                    if(found.isPresent()) {
                        if(flow.userId()!=null && !flow.userId().equals(found.get().getUserId())) {
                            req.getSession().invalidate(); res.sendError(409); return;
                        }
                        join.issue(found.get(),res);
                        req.getSession().invalidate(); res.sendRedirect("/"); return;
                    }
                    if(flow.kind().equals("login") && flow.userId()==null) {
                        req.getSession().invalidate(); res.sendRedirect("/auth/google?unlinked"); return;
                    }
                    req.changeSessionId();
                    req.getSession().setAttribute("google.identity",new GoogleEnrollment.Identity(user.getSubject(),
                        email.strip().toLowerCase(Locale.ROOT),flow.kind().equals("student")?null:user.getFullName()));
                    res.sendRedirect("/auth/google/complete");
                })
                .failureHandler((req,res,error) -> {
                    var session=req.getSession(false);
                    if(session!=null) session.invalidate();
                    res.sendRedirect("/auth/google?failed");
                }));
        return http.build();
    }
}
