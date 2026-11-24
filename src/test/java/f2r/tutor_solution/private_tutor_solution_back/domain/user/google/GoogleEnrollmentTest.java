package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleEnrollmentTest {
    @Test void underFourteenNewSignupStopsBeforeGoogle() {
        var join=new GoogleJoinController(null,null,null,null,null);
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        var model=new org.springframework.ui.ExtendedModelMap();
        join.entry(null,req,model);
        for (String kind : List.of("student", "general", "teacher")) {
            assertEquals("auth/google-guardian",join.choose((String)model.get("nonce"),kind,false,true,req));
        }
        assertEquals("entry",GoogleJoinController.flow(req).kind());
        assertEquals("redirect:/oauth2/authorization/google-student",
            join.choose((String)model.get("nonce"),"login",false,true,req));
    }
    @Test void approvedRouteRequiresValidCodeAndFreshAnonymousEntry() {
        var approvals=mock(EnrolledSignupApproval.class);
        var join=new GoogleJoinController(null,null,null,null,approvals);
        var req=new org.springframework.mock.web.MockHttpServletRequest();
        var model=new org.springframework.ui.ExtendedModelMap();
        join.enrolledEntry(null,req,model);
        String nonce=(String)model.get("nonce");
        assertThrows(ResponseStatusException.class,()->join.enrolledStart("wrong","invalid",true,req));
        verifyNoInteractions(approvals);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(approvals).checkCode("invalid");
        assertThrows(ResponseStatusException.class,()->join.enrolledStart(nonce,"invalid",true,req));
        assertNull(req.getSession().getAttribute(GoogleJoinController.INVITE));
        assertEquals("redirect:/oauth2/authorization/google-student",join.enrolledStart(nonce,"issued",true,req));
        assertEquals("issued",req.getSession().getAttribute(GoogleJoinController.INVITE));
        assertEquals("student",GoogleJoinController.flow(req).kind());
    }

    @Test void studentDataDropsUnrequestedContactAndTeacherFields() {
        var input=new GoogleEnrollment.Form("student","가상학교","중2","unused","unused","unused","unused","yes");
        var profile=GoogleEnrollment.validate(input);
        assertEquals("중2",profile.grade()); assertNull(profile.contact()); assertNull(profile.academy());
    }
    @Test void teacherNeedsContactAndGeneralHasNoSupplementalFields() {
        assertThrows(ResponseStatusException.class,()->GoogleEnrollment.validate(
            new GoogleEnrollment.Form("teacher",null,null,"학원","영어","강남","","yes")));
        var profile=GoogleEnrollment.validate(new GoogleEnrollment.Form("general","unused","unused","unused","unused","unused","unused","yes"));
        assertNull(profile.school()); assertNull(profile.contact());
    }
    @Test void matchedAddressAloneCannotBindALegacyAccount() {
        var users=mock(UserRepository.class); var accounts=mock(SocialAccountRepository.class); var db=mock(JdbcTemplate.class);
        var enroll=new GoogleEnrollment(users,accounts,db,mock(PasswordEncoder.class));
        when(db.queryForList(anyString(),eq(Long.class),eq("a@example.org"),eq("a@example.org"))).thenReturn(List.of(1L));
        assertThrows(ResponseStatusException.class,()->enroll.finish(
            new GoogleEnrollment.Identity("subject","a@example.org","A"),null,null));
        verify(accounts,never()).saveAndFlush(any()); verifyNoInteractions(users);
    }
}
