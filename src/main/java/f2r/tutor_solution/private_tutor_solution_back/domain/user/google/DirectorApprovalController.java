package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("google-accounts")
@RequiredArgsConstructor
@RequestMapping("/academy/enrolled-approvals")
public class DirectorApprovalController {
    private final EnrolledSignupApproval approvals;
    private static final String NONCE="enrolled.approvals.nonce";
    private String page(AuthenticatedPrincipal principal, Long business, HttpServletRequest req, HttpServletResponse res, Model model) {
        var allowed=approvals.businesses(principal);
        if (allowed.isEmpty() || (business!=null && allowed.stream().noneMatch(b->b.id()==business)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long selected=business==null ? allowed.getFirst().id() : business;
        res.setHeader("Cache-Control","no-store");
        String nonce=UUID.randomUUID().toString();
        req.getSession().setAttribute(NONCE,nonce);
        model.addAttribute("nonce",nonce);
        model.addAttribute("businesses",allowed);
        model.addAttribute("businessId",selected);
        model.addAttribute("students",approvals.roster(selected));
        return "auth/director-approvals";
    }

    @GetMapping
    public String view(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam(required=false) Long business,
            HttpServletRequest req,HttpServletResponse res,Model model) {
        return page(p,business,req,res,model);
    }

    @PostMapping
    public String decide(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam long business,
            @RequestParam long student,@RequestParam String action,@RequestParam String nonce,
            @RequestParam(defaultValue="") String email,@RequestParam(defaultValue="") String method,
            @RequestParam(defaultValue="false") boolean verified,HttpServletRequest req,HttpServletResponse res,Model model) {
        var session=req.getSession(false);
        if (session==null || !nonce.equals(session.getAttribute(NONCE))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String view=page(p,business,req,res,model);
        if ("revoke".equals(action)) {
            approvals.revoke(business,student);
            model.addAttribute("message","선택한 학생의 미사용 승인 코드를 취소했습니다.");
        } else if ("approve".equals(action) && verified) {
            model.addAttribute("code",approvals.issue(business,student,p.userId(),email,method));
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"법정대리인의 동의를 먼저 확인해 주세요.");
        return view;
    }
}
