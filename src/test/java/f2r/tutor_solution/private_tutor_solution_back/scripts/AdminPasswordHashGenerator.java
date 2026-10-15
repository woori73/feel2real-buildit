package f2r.tutor_solution.private_tutor_solution_back.scripts;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * init.sql에 박을 ADMIN 시드 비밀번호 BCrypt 해시 생성용 일회성 스크립트.
 * 비밀번호 변경 시 password 상수만 바꾸고 다시 실행하여 출력값을 init.sql에 반영한다.
 */
class AdminPasswordHashGenerator {

    @Test
    void printAdminBcryptHash() {
        String password = "admin1234";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);

        System.out.println("=== ADMIN BCrypt hash ===");
        System.out.println("password: " + password);
        System.out.println("hash:     " + hash);
        System.out.println("verify:   " + encoder.matches(password, hash));
        System.out.println("=========================");
    }
}
