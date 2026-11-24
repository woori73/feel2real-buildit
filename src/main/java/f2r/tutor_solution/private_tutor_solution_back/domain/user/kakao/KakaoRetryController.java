package f2r.tutor_solution.private_tutor_solution_back.domain.user.kakao;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class KakaoRetryController {
    @GetMapping("/auth/kakao/retry")
    public String retry() { return "auth/kakao-retry"; }
}
