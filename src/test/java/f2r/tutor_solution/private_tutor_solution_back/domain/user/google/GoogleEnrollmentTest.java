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
