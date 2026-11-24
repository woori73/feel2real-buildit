package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class EnrolledApprovalMySqlTest {
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    EnrolledSignupApproval approvals;
    User director;
    long business,student;
    String email="child@example.org";
    GoogleEnrollment.Form form=new GoogleEnrollment.Form("student","가상학교","중1",null,null,null,null,null);

    @BeforeEach void setup() {
        String name="test"+UUID.randomUUID().toString().substring(0,8);
        director=users.saveAndFlush(new User(name,"unused","테스트","테스트",null,null,Role.ADMIN));
        approvals=new EnrolledSignupApproval(db,director.getUserId());
        business=db.queryForObject("SELECT business_id FROM easy_businesses WHERE code='ACADEMY'",Long.class);
        db.update("INSERT INTO easy_students(name,status,parent_phone) VALUES ('가상 학생','ENROLLED','fixture')");
        student=lastId();
        db.update("INSERT INTO easy_classes(business_id,name) VALUES (?,'가상 반')",business);
        long group=lastId();
        db.update("INSERT INTO easy_class_members(class_id,student_id,assigned_on) VALUES (?,?,CURRENT_DATE)",group,student);
    }
    private long lastId() {return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    private String issue() {return approvals.issue(business,student,director.getUserId(),email,"paper");}
    private GoogleEnrollment.Identity identity(String address) {return new GoogleEnrollment.Identity("test-sub",address,null);}

    @Test void approvedEmailCanUseTheLatestCodeOnlyOnce() {
        var writer=mock(GoogleEnrollment.class);
        when(writer.finish(any(),eq(form),isNull())).thenReturn(director);
        String first=issue(),second=issue();
        assertThrows(ResponseStatusException.class,()->approvals.checkCode(first));
        assertThrows(ResponseStatusException.class,()->approvals.enroll(second,identity("other@example.org"),form,writer));
        verifyNoInteractions(writer);
        approvals.enroll(second,identity(email),form,writer);
        assertThrows(ResponseStatusException.class,()->approvals.enroll(second,identity(email),form,writer));
        assertThrows(ResponseStatusException.class,this::issue);
        assertEquals(director.getUserId(),db.queryForObject("SELECT used_by FROM enrolled_signup_approvals WHERE student_id=?",Long.class,student));
    }

    @Test void businessScopeWithdrawalExpiryAndRevocationAreEnforced() {
        var manager=new AuthenticatedPrincipal(director.getUserId(),director.getLoginId(),"ADMIN",null);
        assertTrue(approvals.businesses(manager).stream().anyMatch(b->b.id()==business));
        assertTrue(approvals.businesses(new AuthenticatedPrincipal(-7L,"other","ADMIN",null)).isEmpty());
        assertThrows(ResponseStatusException.class,()->approvals.issue(-1,student,director.getUserId(),email,"paper"));
        assertThrows(ResponseStatusException.class,()->approvals.issue(business,student,director.getUserId(),email,"unchecked"));
        db.update("UPDATE easy_students SET parent_phone=NULL WHERE student_id=?",student);
        assertThrows(ResponseStatusException.class,()->approvals.issue(business,student,director.getUserId(),email,"phone"));
        String code=issue(); approvals.revoke(business,student);
        assertThrows(ResponseStatusException.class,()->approvals.checkCode(code));
        String expired=issue();
        db.update("UPDATE enrolled_signup_approvals SET expires_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE) WHERE student_id=?",student);
        assertThrows(ResponseStatusException.class,()->approvals.checkCode(expired));
        String last=issue();
        db.update("UPDATE easy_students SET status='WITHDRAWN' WHERE student_id=?",student);
        assertThrows(ResponseStatusException.class,()->approvals.enroll(last,identity(email),form,mock(GoogleEnrollment.class)));
    }
}
