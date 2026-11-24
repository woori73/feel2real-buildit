package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import f2r.tutor_solution.private_tutor_solution_back.config.security.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("google-accounts")
public class GoogleJoinController {
    static final String FLOW = "google.join.flow";
    record Flow(String kind, String nonce, Long userId, Instant expires) implements java.io.Serializable {}
    static final String INVITE = "google.enrolled.invite";
    private final EnrolledSignupApproval approvals;
    private final GoogleEnrollment enrollment;
    private final JwtTokenProvider tokens;
    private final CookieUtil cookies;
    private final AdminSessionValidator admins;
    public GoogleJoinController(GoogleEnrollment enrollment, JwtTokenProvider tokens, CookieUtil cookies, AdminSessionValidator admins, EnrolledSignupApproval approvals) {
        this.approvals=approvals;
        this.enrollment=enrollment; this.tokens=tokens; this.cookies=cookies; this.admins=admins;
    }

    @GetMapping("/auth/google")
    public String entry(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal, HttpServletRequest req, Model model) {
        var session=req.getSession();
        session.removeAttribute("google.identity");
        session.removeAttribute(INVITE);
        var flow=new Flow("entry",UUID.randomUUID().toString(),principal==null?null:principal.userId(),Instant.now().plusSeconds(600));
        session.setAttribute(FLOW,flow);
        model.addAttribute("nonce",flow.nonce());
        model.addAttribute("unlinked",req.getParameter("unlinked") != null);
        model.addAttribute("failed",req.getParameter("failed") != null);
        return "auth/google-entry";
    }

    static Flow flow(HttpServletRequest req) {
        var session=req.getSession(false);
        if(session==null || !(session.getAttribute(FLOW) instanceof Flow f) || Instant.now().isAfter(f.expires()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"가입 화면을 다시 열어 주세요.");
        return f;
    }

    @PostMapping("/auth/google/choose")
    public String choose(@RequestParam String nonce,@RequestParam String kind,@RequestParam(defaultValue="false") boolean age14,
                         @RequestParam(defaultValue="false") boolean agree,HttpServletRequest req) {
        Flow f=flow(req);
        if(!f.nonce().equals(nonce)||!"entry".equals(f.kind())||!Set.of("student","teacher","general","login").contains(kind))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(!age14 && !kind.equals("login")) return "auth/google-guardian";
        if(!agree) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"수집 안내를 확인해 주세요.");
        req.getSession().setAttribute(FLOW,new Flow(kind,UUID.randomUUID().toString(),f.userId(),f.expires()));
        return "redirect:/oauth2/authorization/"+(kind.equals("student")||kind.equals("login")?"google-student":"google");
    }

    @GetMapping("/auth/google/enrolled")
    public String enrolledEntry(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal, HttpServletRequest req, Model model) {
        entry(principal,req,model);
        return "auth/google-enrolled";
    }

    @PostMapping("/auth/google/enrolled")
    public String enrolledStart(@RequestParam String nonce,@RequestParam String code,
            @RequestParam(defaultValue="false") boolean agree,HttpServletRequest req) {
        Flow f=flow(req);
        if (!agree || !"entry".equals(f.kind()) || !f.nonce().equals(nonce) || f.userId()!=null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        approvals.checkCode(code);
        req.getSession().setAttribute(INVITE,code);
        req.getSession().setAttribute(FLOW,new Flow("student",UUID.randomUUID().toString(),null,f.expires()));
        return "redirect:/oauth2/authorization/google-student";
    }

    @GetMapping("/auth/google/complete")
    public String fields(HttpServletRequest req, Model model) {
        Flow f=flow(req);
        if(req.getSession().getAttribute("google.identity")==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("kind",f.kind()); model.addAttribute("nonce",f.nonce());
        return "auth/google-fields";
    }

    @PostMapping("/auth/google/complete")
    public String complete(@RequestParam Map<String,String> p, @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
                           HttpServletRequest req, HttpServletResponse res) {
        Flow f=flow(req);
        if(!f.nonce().equals(p.get("nonce")) || !Objects.equals(principal==null?null:principal.userId(),f.userId())
            || !(req.getSession().getAttribute("google.identity") instanceof GoogleEnrollment.Identity id))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var form=new GoogleEnrollment.Form(f.kind(),p.get("school"),p.get("grade"),p.get("academy"),p.get("subject"),p.get("area"),p.get("contact"),p.get("business"));
        Object invite=req.getSession().getAttribute(INVITE);
        User user=invite instanceof String code ? approvals.enroll(code,id,form,enrollment) : enrollment.finish(id,form,f.userId());
        issue(user,res);
        req.getSession().invalidate();
        return "redirect:/";
    }

    void issue(User user,HttpServletResponse res) {
        String role=user.getRole().name();
        cookies.writeAccessToken(res,tokens.issue(user.getUserId(),user.getLoginId(),role,
            role.equals("ADMIN")?admins.stampFor(user.getUserId()):null,user.isPasswordChangeRequired()));
    }
}
