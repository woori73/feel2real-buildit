package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** classpath:help/academycraft/*.md 를 기동 시 로드한다. 문서가 깨져 있으면 기동 단계에서 바로 실패시킨다. */
@Component
public class HelpCatalog {

  private final List<HelpDoc> docs;
  private final HelpAccessPolicy policy;

  @Autowired
  public HelpCatalog(
      @Value("${buildit.help.academycraft.ai-official-statuses:REVIEWED}") List<String> aiStatuses) throws IOException {
    this.policy = new HelpAccessPolicy(java.util.Set.copyOf(aiStatuses));
    Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:help/academycraft/*.md");
    this.docs = java.util.Arrays.stream(resources)
        .map(r -> {
          try {
            return HelpDocParser.parse(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new IllegalStateException("도움말 로드 실패: " + r.getFilename(), e);
          }
        })
        .sorted(Comparator.comparingInt(HelpDoc::order))
        .toList();
  }

  HelpCatalog(List<HelpDoc> docs, HelpAccessPolicy policy) {
    this.docs = docs;
    this.policy = policy;
  }

  public HelpAccessPolicy policy() {
    return policy;
  }

  public List<HelpDoc> viewable(String role) {
    return docs.stream().filter(d -> policy.canView(d, role)).toList();
  }

  public List<HelpDoc> aiSources(String role) {
    return docs.stream().filter(d -> policy.canUseForAi(d, role)).toList();
  }

  public Optional<HelpDoc> find(String slug, String role) {
    return viewable(role).stream().filter(d -> d.slug().equals(slug)).findFirst();
  }
}
