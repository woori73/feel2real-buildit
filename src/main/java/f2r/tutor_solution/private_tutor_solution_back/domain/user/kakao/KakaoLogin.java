package f2r.tutor_solution.private_tutor_solution_back.domain.user.kakao;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NoOpOAuth2AuthorizedClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@Configuration
@ConditionalOnProperty(prefix = "f2r.kakao", name = {"client-id", "client-secret"})
public class KakaoLogin {
    static final String START = "/auth/kakao/quick";
    static final String CALLBACK = "/login/oauth2/code/kakao";
    static final String ORIGIN = "https://feel2real.co.kr";

    @ControllerAdvice
    @ConditionalOnProperty(prefix = "f2r.kakao", name = {"client-id", "client-secret"})
    static class ViewSettings {
        @ModelAttribute("kakaoLoginEnabled")
        public boolean enabled() { return true; }
    }

    @Bean
    @Order(2)
    SecurityFilterChain kakaoChain(HttpSecurity http, KakaoMembers members, JwtTokenProvider tokens,
                                   CookieUtil cookies, AdminSessionValidator admins,
                                   @Value("${f2r.kakao.client-id}") String clientId,
                                   @Value("${f2r.kakao.client-secret}") String clientSecret) throws Exception {
        var client = ClientRegistration.withRegistrationId("kakao")
            .clientId(clientId).clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .tokenUri("https://kauth.kakao.com/oauth/token")
            .userInfoUri("https://kapi.kakao.com/v2/user/me")
            .userNameAttributeName("id").redirectUri(ORIGIN + CALLBACK).clientName("카카오").build();
        var clients = new InMemoryClientRegistrationRepository(client);
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        http.securityMatcher(START, CALLBACK)
            .csrf(csrf -> csrf.ignoringRequestMatchers(START))
            .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .oauth2Login(login -> login.clientRegistrationRepository(clients)
                .authorizedClientRepository(new NoOpOAuth2AuthorizedClientRepository())
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(new OAuth2AuthorizationRequestResolver() {
                    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                        return validStart(request) ? resolver.resolve(request, "kakao") : null;
                    }
                    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registration) {
                        return "kakao".equals(registration) ? resolve(request) : null;
                    }
                }))
                .successHandler((req, res, auth) -> {
                    var identity = ((OAuth2AuthenticationToken) auth).getPrincipal();
                    String subject = identity.getName();
                    if (!subject.matches("[1-9][0-9]{0,18}")) {
                        res.sendRedirect("/auth/kakao/retry");
                        return;
                    }
                    User user;
                    try {
                        user = members.resolve(subject);
                    } catch (DataIntegrityViolationException race) {
                        user = members.linked(subject).orElseThrow(() -> race);
                    }
                    cookies.writeAccessToken(res, tokens.issue(user.getUserId(), user.getLoginId(), user.getRole().name(),
                        user.getRole() == Role.ADMIN ? admins.stampFor(user.getUserId()) : null, user.isPasswordChangeRequired()));
                    res.sendRedirect("/");
                })
                .failureHandler((req, res, error) -> res.sendRedirect("/auth/kakao/retry")));
        return http.build();
    }

    static boolean validStart(HttpServletRequest request) {
        return START.equals(request.getRequestURI()) && "POST".equals(request.getMethod())
            && ORIGIN.equals(request.getHeader("Origin")) && "true".equals(request.getParameter("confirm14"));
    }
}
