package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** 기존 회원·소유 학원 연결만 사용. 브라우저가 이력의 사용자/학원/토큰을 지정할 수 없다. */
@Service
public class AdmissionsMemberAccess {
    private final UserRepository users;
    private final AcademyRepository academies;
    private final String secret;
    public AdmissionsMemberAccess(UserRepository users, AcademyRepository academies,
                                  @Value("${app.jwt.secret}") String secret) {
        this.users=users; this.academies=academies; this.secret=secret;
    }
    @Transactional(readOnly=true)
    public String token(AuthenticatedPrincipal p) {
        if (p==null || p.userId()==null) throw new AccessDeniedException("로그인이 필요합니다.");
        var u=users.findById(p.userId()).orElseThrow(() -> new AccessDeniedException("회원 정보를 확인할 수 없습니다."));
        String role=u.getRole().name();
        if (!role.equals(p.role()) || !(role.equals("ADMIN") || role.equals("TUTOR")))
            throw new AccessDeniedException("관리자 또는 강사 권한이 필요합니다.");
        Long academyId=role.equals("ADMIN") ? 0L : academies.findByTutor_UserId(u.getUserId())
                .orElseThrow(() -> new AccessDeniedException("연결된 학원이 없습니다.")).getAcademyId();
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return "member_"+HexFormat.of().formatHex(mac.doFinal(
                    ("admissions:v1:"+role+":"+u.getUserId()+":"+academyId).getBytes(StandardCharsets.UTF_8))).substring(0,56);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("상담 이력을 확인할 수 없습니다."); }
    }
}
