package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GoogleSignupMode {
    private final Environment env;
    public GoogleSignupMode(Environment env) { this.env=env; }
    @ModelAttribute("googleSignup")
    public boolean enabled() { return env.acceptsProfiles(Profiles.of("google-accounts")); }
}
