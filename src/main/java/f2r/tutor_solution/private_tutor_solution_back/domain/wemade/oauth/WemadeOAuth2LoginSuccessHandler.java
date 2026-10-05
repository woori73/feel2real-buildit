package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.oauth;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverAccountResolutionService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.oauth.NaverProfileIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * /wemade 로그인 복귀용 핸들러.
 *
 * 기존 {@code NaverOAuth2LoginSuccessHandler}는 수정하지 않는다.
 * - registrationId "naver-wemade" → 이 핸들러가 동일한 보안 원칙(쿠키 발급 정책,
 *   JWT를 URL에 싣지 않음, 네이버 토큰 미취급)으로 처리하고 /wemade로 redirect
 * - 그 외(기존 "naver") → 생성자로 받은 기존 핸들러(delegate)에 위임. 기존 동작 100% 동일.
 */
public class WemadeOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(WemadeOAuth2LoginSuccessHandler.class);
    static final String WEMADE_REGISTRATION_ID = "naver-wemade";
    static final String WEMADE_REDIRECT_PATH = "/wemade";

    private final NaverAccountResolutionService accountResolutionService;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtil cookieUtil;
    private final NewStartMemberProfileService memberProfileService;
    private final AuthenticationSuccessHandler delegate;

    public WemadeOAuth2LoginSuccessHandler(
            NaverAccountResolutionService accountResolutionService,
            JwtTokenProvider tokenProvider,
            CookieUtil cookieUtil,
            NewStartMemberProfileService memberProfileService,
            AuthenticationSuccessHandler delegate) {
        this.accountResolutionService = accountResolutionService;
        this.tokenProvider = tokenProvider;
        this.cookieUtil = cookieUtil;
        this.memberProfileService = memberProfileService;
        this.delegate = delegate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException {
        boolean wemade = authentication instanceof OAuth2AuthenticationToken token
                && WEMADE_REGISTRATION_ID.equals(token.getAuthorizedClientRegistrationId());
        if (!wemade) {
            delegate.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        // --- 아래는 기존 핸들러와 동일한 보안 원칙의 처리. redirect 목적지만 /wemade ---
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Object idAttr = oauth2User.getAttribute("id"); // NaverProfileParser.ID_KEY("id")가 패키지-private이므로 리터럴 사용
        boolean providerIdPresent = idAttr != null && !String.valueOf(idAttr).isBlank();
        log.info("NAVER_OAUTH_SUCCESS providerIdPresent={} registration=wemade", providerIdPresent);

        if (accountResolutionService == null || tokenProvider == null || cookieUtil == null) {
            log.warn("NAVER_OAUTH_FAILURE stage=step3_dependency_unavailable registration=wemade");
            writeJsonError(response);
            return;
        }
        User user;
        try {
            NaverProfileIdentity identity = NaverProfileIdentity.from(oauth2User);
            user = accountResolutionService.resolveUser(identity);
            if (memberProfileService != null) {
                try {
                    memberProfileService.ensureSeeded(user.getUserId(), identity.nickname());
                } catch (RuntimeException seedEx) {
                    log.warn("NAVER_OAUTH_NICKNAME_SEED_FAILURE", seedEx);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("NAVER_OAUTH_FAILURE stage=account_resolution_failure registration=wemade", ex);
            writeJsonError(response);
            return;
        }
        String token = tokenProvider.issue(user.getUserId(), user.getLoginId(), user.getRole().name());
        cookieUtil.writeAccessToken(response, token);
        log.info("NAVER_OAUTH_LOGIN_COMPLETE registration=wemade");
        response.sendRedirect(WEMADE_REDIRECT_PATH);
    }

    private void writeJsonError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"provider\":\"NAVER\",\"success\":false}");
    }
}
