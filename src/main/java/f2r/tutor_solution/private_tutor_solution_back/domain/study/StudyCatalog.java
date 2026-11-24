package f2r.tutor_solution.private_tutor_solution_back.domain.study;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class StudyCatalog {
    public record Topic(String collection, String section, String slug, String title,
                        String guide, String q, String ans, String exp, String source) {}
    public record Daily(String topic, String date, String title, String guide,
                        String q, String ans, String exp) {}

    public record Faq(String q, String a) {}
    public record Guide(String section, String slug, String title, String lead, String body, List<Faq> faq,
                        String sourceUrl, String sourceName, LocalDate published, LocalDate modified) {}

    final Map<String, Guide> guides = new LinkedHashMap<>();
    final Map<String, List<Guide>> guideGroups = new LinkedHashMap<>();
    final Map<String, Topic> topics = new LinkedHashMap<>();
    final Map<String, Map<String, List<Topic>>> groups = new LinkedHashMap<>();
    final Map<String, NavigableMap<String, Daily>> updates = new HashMap<>();

    public StudyCatalog(ObjectMapper mapper) throws IOException {
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
        try (var stream = new org.springframework.core.io.ClassPathResource("study-hub/guides.json").getInputStream()) {
            for (var guide : mapper.readValue(stream, Guide[].class)) {
                if (topics.containsKey(guide.slug()) || guides.putIfAbsent(guide.slug(), guide) != null) {
                    throw new IllegalArgumentException("Duplicate study guide " + guide.slug());
                }
                guideGroups.computeIfAbsent(guide.section(), key -> new ArrayList<>()).add(guide);
            }
        }
        for (var resource : resolver.getResources("classpath*:study-hub/daily/*.json")) {
            try (var stream = resource.getInputStream()) {
                var day = mapper.readValue(stream, Daily.class);
                LocalDate.parse(day.date());
                if ((!topics.containsKey(day.topic()) && !guides.containsKey(day.topic())) || !(day.date() + ".json").equals(resource.getFilename())) {
                    throw new IllegalArgumentException("Invalid study update " + resource.getFilename());
                }
                updates.computeIfAbsent(day.topic(), key -> new TreeMap<>(Comparator.reverseOrder()))
                       .put(day.date(), day);
            }
        }
    }

    public Map<String, LocalDate> canonicalDates() {
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        topics.keySet().forEach(slug -> dates.put("/study/" + slug + "/", LocalDate.of(2026, 11, 24)));
        guides.forEach((slug, guide) -> dates.put("/study/" + slug + "/", guide.modified()));
        updates.forEach((slug, days) -> {
            String base = "/study/" + slug + "/";
            days.keySet().forEach(day -> dates.put(base + day, LocalDate.parse(day)));
            LocalDate latest = LocalDate.parse(days.firstKey());
            if (latest.isAfter(dates.get(base))) dates.put(base, latest);
        });
        return dates;
    }
}
