package f2r.tutor_solution.private_tutor_solution_back.domain.user.kakao;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KakaoLoginTest {
    @Test void onlySameOriginConfirmedPostStartsSignup() {
        var request = new MockHttpServletRequest("POST", KakaoLogin.START);
        request.addParameter("confirm14", "true");
        assertThat(KakaoLogin.validStart(request)).isFalse();
        request.addHeader("Origin", KakaoLogin.ORIGIN);
        assertThat(KakaoLogin.validStart(request)).isTrue();
        request.setMethod("GET");
        assertThat(KakaoLogin.validStart(request)).isFalse();
        request.setMethod("POST");
        request.removeParameter("confirm14");
        assertThat(KakaoLogin.validStart(request)).isFalse();
    }

    @Test void returningMemberKeepsAccountAndRole() {
        var accounts = mock(SocialAccountRepository.class);
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var link = SocialAccount.builder().userId(7L).provider(SocialProvider.KAKAO).providerUserId("123").build();
        var member = User.builder().role(Role.TUTOR).build();
        when(accounts.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123")).thenReturn(Optional.of(link));
        when(users.findById(7L)).thenReturn(Optional.of(member));
        assertThat(new KakaoMembers(accounts, users, passwords).resolve("123")).isSameAs(member);
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords);
    }

    @Test void firstSignupCreatesGeneralMemberWithoutEmail() {
        var accounts = mock(SocialAccountRepository.class);
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var member = new KakaoMembers(accounts, users, passwords).resolve("123");
        assertThat(member.getRole()).isEqualTo(Role.STUDENT);
        assertThat(member.getEmail()).isNull();
        verify(accounts).saveAndFlush(argThat(a -> a.getProvider() == SocialProvider.KAKAO && "123".equals(a.getProviderUserId())));
    }
}
