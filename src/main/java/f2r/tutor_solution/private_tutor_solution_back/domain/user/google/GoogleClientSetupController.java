package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminPasswordChangeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("prd")
@RequiredArgsConstructor
@RequestMapping("/admin/google-login-setup")
public class GoogleClientSetupController {
    private static final String NONCE = "google.setup.nonce";
    private final AdminSessionValidator sessions;
    private final GoogleSignupMode mode;

    private void authorize(AuthenticatedPrincipal p, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (p == null || !"ADMIN".equals(p.role()) || !AdminPasswordChangeService.ADMIN_LOGIN_ID.equals(p.loginId())
                || p.credentialStamp() == null || !sessions.isValid(p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        authorize(p, response);
        boolean active = mode.enabled();
        boolean waiting = Files.exists(GoogleClientSetup.FILE);
        if (!active && !waiting) {
            String nonce = UUID.randomUUID().toString();
            request.getSession().setAttribute(NONCE, nonce);
            model.addAttribute("nonce", nonce);
        }
        model.addAttribute("active", active);
        model.addAttribute("waiting", waiting);
        return "admin/google-login-setup";
    }

    @PostMapping
    public String upload(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String nonce,
            @RequestParam MultipartFile file, HttpServletRequest request, HttpServletResponse response,
            Model model) throws IOException {
        authorize(p, response);
        var session = request.getSession(false);
        if (!"https://feel2real.co.kr".equals(request.getHeader("Origin"))
                || session == null || !nonce.equals(session.getAttribute(NONCE))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        session.removeAttribute(NONCE);
        if (mode.enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        try (var input = file.getInputStream()) {
            GoogleClientSetup.register(GoogleClientSetup.FILE, input);
        } catch (FileAlreadyExistsException alreadyRegistered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        } catch (IllegalArgumentException invalidFile) {
            response.setStatus(400);
            model.addAttribute("error", invalidFile.getMessage());
            return page(p,request,response,model);
        }
        return "redirect:/admin/google-login-setup";
    }
}
