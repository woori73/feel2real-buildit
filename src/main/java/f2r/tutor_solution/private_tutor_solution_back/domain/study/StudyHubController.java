package f2r.tutor_solution.private_tutor_solution_back.domain.study;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class StudyHubController {
    private final StudyCatalog catalog;

    public StudyHubController(StudyCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"/study", "/study/"})
    public String index(Model model) {
        model.addAttribute("guideGroups", catalog.guideGroups);
        return "public/study/index";
    }

    @GetMapping("/study/archive/")
    public String archive(Model model) {
        model.addAttribute("groups", catalog.groups);
        model.addAttribute("count", catalog.topics.size());
        return "public/study/archive";
    }

    @GetMapping({"/study/{slug}", "/study/{slug}/"})
    public String topic(@PathVariable("slug") String slug, Model model) {
        var guide = catalog.guides.get(slug);
        if (guide != null) {
            model.addAttribute("guide", guide);
            model.addAttribute("base", "/study/" + slug + "/");
            model.addAttribute("updates", catalog.updates.getOrDefault(slug, Collections.emptyNavigableMap()).values());
            return "public/study/guide";
        }
        var topic = catalog.topics.get(slug);
        if (topic == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("topic", topic);
        model.addAttribute("updates", catalog.updates.getOrDefault(slug, Collections.emptyNavigableMap()).values());
        model.addAttribute("base", "/study/" + slug + "/");
        model.addAttribute("item", topic);
        model.addAttribute("canonical", "/study/" + slug + "/");
        return "public/study/detail";
    }

    @GetMapping("/study/{slug}/{date}")
    public String daily(@PathVariable("slug") String slug, @PathVariable("date") String date, Model model) {
        topic(slug, model);
        var day = catalog.updates.getOrDefault(slug, Collections.emptyNavigableMap()).get(date);
        if (day == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("day", day);
        model.addAttribute("item", day);
        model.addAttribute("canonical", "/study/" + slug + "/" + date);
        return "public/study/detail";
    }
}
