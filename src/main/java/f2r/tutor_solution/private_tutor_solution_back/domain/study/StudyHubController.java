package f2r.tutor_solution.private_tutor_solution_back.domain.study;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Controller
public class StudyHubController {
    public record Topic(String collection, String section, String slug, String title,
                        String guide, String q, String ans, String exp, String source) {}
    public record Daily(String topic, String date, String title, String guide,
                        String q, String ans, String exp) {}

    private final Map<String, Topic> topics = new LinkedHashMap<>();
    private final Map<String, Map<String, List<Topic>>> groups = new LinkedHashMap<>();
    private final Map<String, NavigableMap<String, Daily>> updates = new HashMap<>();

    public StudyHubController(ObjectMapper mapper) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] catalog = resolver.getResources("classpath*:study-hub/catalog-*.json");
        Arrays.sort(catalog, Comparator.comparing(Resource::getFilename));
        for (var resource : catalog) {
            try (var stream = resource.getInputStream()) {
                for (Topic topic : mapper.readValue(stream, Topic[].class)) {
                    if (topics.putIfAbsent(topic.slug(), topic) != null) {
                        throw new IllegalArgumentException("Duplicate study topic " + topic.slug());
                    }
                    groups.computeIfAbsent(topic.collection(), key -> new LinkedHashMap<>())
                          .computeIfAbsent(topic.section(), key -> new ArrayList<>()).add(topic);
                }
            }
        }
        for (var resource : resolver.getResources("classpath*:study-hub/daily/*.json")) {
            try (var stream = resource.getInputStream()) {
                var day = mapper.readValue(stream, Daily.class);
                LocalDate.parse(day.date());
                if (!topics.containsKey(day.topic()) || !(day.date() + ".json").equals(resource.getFilename())) {
                    throw new IllegalArgumentException("Invalid study update " + resource.getFilename());
                }
                updates.computeIfAbsent(day.topic(), key -> new TreeMap<>(Comparator.reverseOrder()))
                       .put(day.date(), day);
            }
        }
    }

    @GetMapping({"/study", "/study/"})
    public String index(Model model) {
        model.addAttribute("groups", groups);
        model.addAttribute("count", topics.size());
        return "public/study/index";
    }

    @GetMapping({"/study/{slug}", "/study/{slug}/"})
    public String topic(@PathVariable("slug") String slug, Model model) {
        var topic = topics.get(slug);
        if (topic == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("topic", topic);
        model.addAttribute("updates", updates.getOrDefault(slug, Collections.emptyNavigableMap()).values());
        model.addAttribute("base", "/study/" + slug + "/");
        model.addAttribute("item", topic);
        model.addAttribute("canonical", "/study/" + slug + "/");
        return "public/study/detail";
    }

    @GetMapping("/study/{slug}/{date}")
    public String daily(@PathVariable("slug") String slug, @PathVariable("date") String date, Model model) {
        String view = topic(slug, model);
        var day = updates.getOrDefault(slug, Collections.emptyNavigableMap()).get(date);
        if (day == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("day", day);
        model.addAttribute("item", day);
        model.addAttribute("canonical", "/study/" + slug + "/" + date);
        return view;
    }
}
