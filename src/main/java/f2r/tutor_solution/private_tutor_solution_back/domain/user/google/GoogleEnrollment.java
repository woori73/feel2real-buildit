package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Google signs the subject and email. An email match only locates a candidate for linking. */
@Service
@Profile("google-accounts")
public class GoogleEnrollment {
    public record Identity(String sub, String email, String name) implements java.io.Serializable {}
    public record Form(String kind, String school, String grade, String academy, String subject,
                       String area, String contact, String business) implements java.io.Serializable {}
    private final UserRepository users;
    private final SocialAccountRepository accounts;
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public GoogleEnrollment(UserRepository users, SocialAccountRepository accounts, JdbcTemplate db, PasswordEncoder encoder) {
        this.users = users; this.accounts = accounts; this.db = db; this.encoder = encoder;
    }

    public Optional<User> linked(String sub) {
        return accounts.findByProviderAndProviderUserId(SocialProvider.GOOGLE, sub).flatMap(a -> users.findById(a.getUserId()));
    }

    private static String required(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return value.strip();
    }

    static Form validate(Form f) {
        return switch (f.kind()) {
            case "student" -> new Form("student", required(f.school(), 80), required(f.grade(), 12), null, null, null, null, null);
            case "teacher" -> {
                if (!Set.of("yes","no").contains(Objects.toString(f.business(),""))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                yield new Form("teacher", null, null, required(f.academy(),80), required(f.subject(),80),
                    required(f.area(),80), required(f.contact(),100), f.business());
            }
            case "general" -> new Form("general", null, null, null, null, null, null, null);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
    }

    @Transactional
    public User finish(Identity id, Form form, Long provenUser) {
        var existing = linked(id.sub());
        if (existing.isPresent()) return existing.get();
        var candidates = db.queryForList(
            "SELECT DISTINCT u.user_id FROM users u LEFT JOIN user_social_accounts a ON a.user_id=u.user_id " +
            "WHERE LOWER(TRIM(u.email))=? OR LOWER(TRIM(a.provider_email))=?", Long.class, id.email(), id.email());
        User user;
        if (!candidates.isEmpty() || provenUser != null) {
            if (provenUser == null || !candidates.contains(provenUser)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 계정 로그인 후 같은 이메일의 Google 계정을 연결해 주세요.");
            }
            user = users.findById(provenUser).orElseThrow();
            if (accounts.findFirstByUserIdAndProvider(provenUser, SocialProvider.GOOGLE).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 연결을 확인해 주세요.");
            }
        } else {
            var f = validate(form);
            String loginId = "g_" + UUID.randomUUID().toString().replace("-","");
            String name = f.kind().equals("student") ? "학생" : Objects.toString(id.name(), "회원");
            if (name.isBlank()) name = "회원";
            user = users.saveAndFlush(User.builder().loginId(loginId).password(encoder.encode(UUID.randomUUID().toString()))
                .name(name.substring(0,Math.min(name.length(),30))).email(id.email())
                .role(f.kind().equals("teacher") ? Role.TUTOR : Role.STUDENT).build());
            db.update("INSERT INTO google_signup_profiles (user_id,kind,school,grade,academy,subject,area,contact,business,notice_version) VALUES (?,?,?,?,?,?,?,?,?,?)",
                user.getUserId(),f.kind(),f.school(),f.grade(),f.academy(),f.subject(),f.area(),f.contact(),f.business(),"2026-11-24");
        }
        accounts.saveAndFlush(SocialAccount.builder().userId(user.getUserId()).provider(SocialProvider.GOOGLE)
            .providerUserId(id.sub()).providerEmail(id.email()).build());
        return user;
    }
}
