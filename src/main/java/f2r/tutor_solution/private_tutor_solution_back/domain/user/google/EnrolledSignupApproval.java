package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** One short-lived authorization per roster entry. Reissuing replaces an unused code. */
@Service
@Profile("google-accounts")
public class EnrolledSignupApproval {
    private final JdbcTemplate db;
    private final long flagshipDirector;
    private final SecureRandom random = new SecureRandom();

    public EnrolledSignupApproval(JdbcTemplate db,
            @org.springframework.beans.factory.annotation.Value("${app.enrolled.flagship-director-id:0}") long flagshipDirector) {
        this.db = db;
        this.flagshipDirector = flagshipDirector;
    }
    public record Business(long id, String name) {}
    public List<Business> businesses(f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal p) {
        if (p==null || !List.of("TUTOR","ADMIN").contains(p.role())) throw invalid();
        return db.query("""
            SELECT DISTINCT b.business_id,b.name FROM easy_businesses b WHERE b.active=1 AND
              (EXISTS (SELECT 1 FROM academy_easy_business_links l JOIN academies a ON a.academy_id=l.academy_id
                       WHERE l.business_id=b.business_id AND a.tutor_user_id=? AND a.status='PUBLISHED' AND a.is_visible=1)
               OR (b.code='ACADEMY' AND ?=1)) ORDER BY b.business_id
            """,(r,i)->new Business(r.getLong(1),r.getString(2)),p.userId(),
            "ADMIN".equals(p.role()) && flagshipDirector>0 && flagshipDirector==p.userId() ? 1 : 0);
    }
    public record Student(long id, String name) {}

    private static final String ACTIVE_ROSTER = """
        FROM easy_students s JOIN easy_class_members m ON m.student_id=s.student_id
        JOIN easy_classes c ON c.class_id=m.class_id JOIN easy_businesses b ON b.business_id=c.business_id
        WHERE b.business_id=? AND b.active=1 AND c.status='ACTIVE' AND s.status='ENROLLED'
          AND m.assigned_on<=CURRENT_DATE AND (m.released_on IS NULL OR m.released_on>CURRENT_DATE)
        """;

    public List<Student> roster(long business) {
        return db.query("SELECT DISTINCT s.student_id,s.name " + ACTIVE_ROSTER + " ORDER BY s.name,s.student_id",
            (r,i)->new Student(r.getLong(1),r.getString(2)),business);
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    static String emailKey(String code, String email) {
        return digest(code + ":" + email.strip().toLowerCase(Locale.ROOT));
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,"승인 코드와 가입할 Google 계정을 원장님에게 확인해 주세요.");
    }

    @Transactional
    public String issue(long business, long student, long director, String email, String method) {
        if (!List.of("paper","phone").contains(method)) throw invalid();
        if (email==null || email.length()>100 || !email.strip().matches("[^ @]+@[^ @]+[.][^ @]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"학생이 사용할 Google 이메일을 확인해 주세요.");
        // Reissues for the same roster entry must replace an unused code atomically.
        if (db.queryForList("SELECT student_id FROM easy_students WHERE student_id=? FOR UPDATE",Long.class,student).isEmpty() ||
            db.queryForList("SELECT s.student_id "+ACTIVE_ROSTER+" AND s.student_id=?",Long.class,business,student).isEmpty())
            throw invalid();
        if ("phone".equals(method) && !Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM easy_students WHERE student_id=? AND NULLIF(TRIM(parent_phone),'') IS NOT NULL)",Boolean.class,student)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"등록된 보호자 번호가 있어야 전화 확인을 선택할 수 있습니다.");
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM enrolled_signup_approvals WHERE student_id=? AND used_at IS NOT NULL)",Boolean.class,student)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 사용된 가입 승인입니다.");
        byte[] bytes=new byte[24]; random.nextBytes(bytes);
        String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM enrolled_signup_approvals WHERE student_id=? AND used_at IS NULL",student);
        db.update("""
            INSERT INTO enrolled_signup_approvals
              (student_id,business_id,approved_by,method,notice_version,code_hash,email_key,expires_at)
            VALUES (?,?,?,?,'2026-11-24',?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 48 HOUR))
            """,student,business,director,method,digest(code),emailKey(code,email));
        return code;
    }

    @Transactional
    public void revoke(long business,long student) {
        db.update("DELETE FROM enrolled_signup_approvals WHERE business_id=? AND student_id=? AND used_at IS NULL",business,student);
    }

    @org.springframework.scheduling.annotation.Scheduled(cron="0 15 3 * * *",zone="Asia/Seoul")
    public void clearExpiredUnused() {
        db.update("DELETE FROM enrolled_signup_approvals WHERE used_at IS NULL AND expires_at<=CURRENT_TIMESTAMP");
    }

    public void checkCode(String code) {
        if (code==null || !code.matches("[A-Za-z0-9_-]{32}") || !Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM enrolled_signup_approvals WHERE code_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP)",
            Boolean.class,digest(code)))) throw invalid();
    }

    @Transactional
    public f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User enroll(
            String code, GoogleEnrollment.Identity id, GoogleEnrollment.Form form, GoogleEnrollment enrollment) {
        if (!"student".equals(form.kind())) throw invalid();
        consume(code,id.email());
        var user=enrollment.finish(id,form,null);
        db.update("UPDATE enrolled_signup_approvals SET used_by=?,code_hash=NULL,email_key=NULL WHERE code_hash=?",user.getUserId(),digest(code));
        return user;
    }

    private void consume(String code,String email) {
        checkCode(code);
        int updated=db.update("""
            UPDATE enrolled_signup_approvals a SET a.used_at=CURRENT_TIMESTAMP
            WHERE a.code_hash=? AND a.email_key=? AND a.used_at IS NULL AND a.expires_at>CURRENT_TIMESTAMP
              AND EXISTS (SELECT 1 FROM easy_students s JOIN easy_class_members m ON m.student_id=s.student_id
                JOIN easy_classes c ON c.class_id=m.class_id JOIN easy_businesses b ON b.business_id=c.business_id
                WHERE s.student_id=a.student_id AND b.business_id=a.business_id AND b.active=1 AND c.status='ACTIVE'
                  AND s.status='ENROLLED' AND m.assigned_on<=CURRENT_DATE
                  AND (m.released_on IS NULL OR m.released_on>CURRENT_DATE))
            """,digest(code),emailKey(code,email));
        if(updated!=1) throw invalid();
    }
}
