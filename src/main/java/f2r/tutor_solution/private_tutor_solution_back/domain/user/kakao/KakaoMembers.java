package f2r.tutor_solution.private_tutor_solution_back.domain.user.kakao;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccount;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoMembers {
    private final SocialAccountRepository accounts;
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public KakaoMembers(SocialAccountRepository accounts, UserRepository users, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.users = users;
        this.passwords = passwords;
    }

    Optional<User> linked(String subject) {
        return accounts.findByProviderAndProviderUserId(SocialProvider.KAKAO, subject)
            .map(link -> users.findById(link.getUserId()).orElseThrow());
    }

    @Transactional
    public User resolve(String subject) {
        return linked(subject).orElseGet(() -> {
            String loginId = "k_" + UUID.randomUUID().toString().replace("-", "");
            User user = users.saveAndFlush(User.builder().loginId(loginId)
                .password(passwords.encode(UUID.randomUUID().toString())).name("카카오 회원")
                .nickname("카카오 회원").role(Role.STUDENT).build());
            accounts.saveAndFlush(SocialAccount.builder().userId(user.getUserId())
                .provider(SocialProvider.KAKAO).providerUserId(subject).build());
            return user;
        });
    }
}
