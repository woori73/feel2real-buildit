package f2r.tutor_solution.web.designlab;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/design-lab")
public class DesignLabController {

    @GetMapping("/home-a")
    public String homeA(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "home-a");
        request.setAttribute("pageTitle", "Design Lab · TYPE A (Vanilla)");
        return "design-lab/home-a";
    }

    @GetMapping("/home-b")
    public String homeB(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "home-b");
        request.setAttribute("pageTitle", "Design Lab · TYPE B (React + Tailwind)");
        return "design-lab/home-b";
    }

    @GetMapping("/home-c")
    public String homeC(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "home-c");
        request.setAttribute("pageTitle", "Design Lab · TYPE C (Hybrid)");
        return "design-lab/home-c";
    }

    @GetMapping("/eduholic-options")
    public String eduholicOptions(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "eduholic-options");
        request.setAttribute("pageTitle", "Design Lab · Eduholic Dynamic UI Gallery");
        return "design-lab/eduholic-options";
    }

    @GetMapping("/eduholic-preview")
    public String eduholicPreview(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "eduholic-preview");
        request.setAttribute("pageTitle", "Design Lab · Eduholic Preview");
        return "design-lab/eduholic-preview";
    }

    @GetMapping("")
    public String index(HttpServletRequest request) {
        request.setAttribute("designLabVariant", "index");
        request.setAttribute("pageTitle", "Design Lab · BuildIt Experiments");
        return "design-lab/index";
    }
}