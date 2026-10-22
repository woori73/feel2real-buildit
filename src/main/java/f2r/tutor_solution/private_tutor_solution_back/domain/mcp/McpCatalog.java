package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runtime mappings are authoritative; a committed exact-path policy is required for execution. */
@Component
public class McpCatalog {
  private final RequestMappingHandlerMapping mapping;
  private final List<Policy> policies;
  private final JsonNode screens;
  private final org.springframework.beans.factory.ObjectProvider<
          org.springframework.web.servlet.handler.SimpleUrlHandlerMapping>
      views;

  public record Policy(
      String path,
      String service,
      List<String> roles,
      String proxy,
      List<String> params,
      String reason) {}

  public McpCatalog(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping,
      ObjectMapper json,
      @Qualifier("viewControllerHandlerMapping")
          org.springframework.beans.factory.ObjectProvider<
                  org.springframework.web.servlet.handler.SimpleUrlHandlerMapping>
              views)
      throws Exception {
    this.mapping = mapping;
    this.views = views;
    try (var in = new ClassPathResource("mcp/screen-sources.json").getInputStream()) {
      screens = json.readTree(in);
    }
    try (var in = new ClassPathResource("mcp/read-policy.json").getInputStream()) {
      this.policies = List.of(json.readValue(in, Policy[].class));
    }
  }

  private volatile List<Map<String, Object>> cached;

  public List<Map<String, Object>> inventory() {
    if (cached != null) return cached;
    synchronized (this) {
      if (cached == null) cached = List.copyOf(loadInventory());
      return cached;
    }
  }

  private List<Map<String, Object>> loadInventory() {
    List<Map<String, Object>> result = new ArrayList<>();
    mapping
        .getHandlerMethods()
        .forEach(
            (info, handler) -> {
              if (!handler.getBeanType().getPackageName().startsWith("f2r.")) return;
              for (String path : info.getPatternValues()) {
                var methods = info.getMethodsCondition().getMethods();
                String controller = handler.getBeanType().getSimpleName();
                boolean read = methods.contains(RequestMethod.GET);
                Policy p =
                    policies.stream().filter(v -> v.path().equals(path)).findFirst().orElse(null);
                List<String> params = new ArrayList<>();
                for (Parameter parameter : handler.getMethod().getParameters()) {
                  var annotation = parameter.getAnnotation(RequestParam.class);
                  if (annotation != null) {
                    String name =
                        !annotation.name().isBlank()
                            ? annotation.name()
                            : !annotation.value().isBlank()
                                ? annotation.value()
                                : parameter.getName();
                    params.add(name);
                  }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(
                    "id",
                    (read
                            ? "GET"
                            : methods.isEmpty()
                                ? "ANY"
                                : methods.stream()
                                    .map(Enum::name)
                                    .sorted()
                                    .collect(java.util.stream.Collectors.joining("|")))
                        + " "
                        + path);
                row.put("path", path);
                row.put("url", "https://feel2real.co.kr" + path);
                row.put(
                    "methods",
                    methods.isEmpty()
                        ? List.of("ANY")
                        : methods.stream().map(Enum::name).sorted().toList());
                row.put("controller", controller);
                row.put("handler", handler.getMethod().getName());
                row.put(
                    "source",
                    "src/main/java/" + handler.getBeanType().getName().replace('.', '/') + ".java");
                row.put(
                    "representation",
                    path.startsWith("/api/")
                        ? "server API"
                        : "server page or proxy; client-side requests are separate sources");
                row.put(
                    "service",
                    p == null
                        ? (handler.getBeanType().getPackageName().contains(".domain.")
                            ? handler
                                .getBeanType()
                                .getPackageName()
                                .split("\\.domain\\.")[1]
                                .split("\\.")[0]
                            : "system")
                        : p.service());
                row.put("readable", read && p != null);
                row.put("params", params);
                row.put("roles", p == null ? List.of() : p.roles());
                row.put(
                    "permission",
                    "Existing SecurityConfig and handler/service ownership checks; catalog presence"
                        + " is not authorization");
                row.put(
                    "reason",
                    read && p != null
                        ? p.reason()
                        : "실행 미허용: 쓰기/초기화/비용 발생/미검토 경로는 조회 도구로 실행할 수 없음");
                result.add(row);
              }
            });
    var viewMapping = views.getIfAvailable();
    if (viewMapping != null)
      viewMapping
          .getHandlerMap()
          .forEach(
              (path, handler) -> {
                if (result.stream().anyMatch(row -> row.get("path").equals(path))) return;
                Policy policy =
                    policies.stream().filter(v -> v.path().equals(path)).findFirst().orElse(null);
                String template =
                    handler
                            instanceof
                            org.springframework.web.servlet.mvc.ParameterizableViewController v
                        ? String.valueOf(v.getViewName())
                        : "";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "GET " + path);
                row.put("path", path);
                row.put("url", "https://feel2real.co.kr" + path);
                row.put("methods", List.of("GET"));
                row.put("controller", "WebMvcConfig");
                row.put("handler", template);
                row.put(
                    "source",
                    "src/main/java/f2r/tutor_solution/private_tutor_solution_back/config/WebMvcConfig.java");
                row.put(
                    "representation",
                    "SSR template shell; dynamic records require the frontend API sources");
                row.put("service", policy == null ? "page" : policy.service());
                row.put("readable", policy != null);
                row.put("params", List.of());
                row.put("roles", policy == null ? List.of() : policy.roles());
                row.put(
                    "permission",
                    "Existing SecurityConfig and interceptors; catalog presence is not"
                        + " authorization");
                row.put("reason", policy == null ? "조회 도구 미허용" : policy.reason());
                row.put("template", template);
                result.add(row);
              });
    for (var p : policies)
      if (p.proxy() != null)
        result.add(
            new LinkedHashMap<>(
                Map.of(
                    "id",
                    "GET " + p.path(),
                    "path",
                    p.path(),
                    "url",
                    "https://feel2real.co.kr" + p.path(),
                    "methods",
                    List.of("GET"),
                    "controller",
                    p.proxy(),
                    "representation",
                    "proxy API; actual upstream data",
                    "service",
                    p.service(),
                    "readable",
                    true,
                    "params",
                    p.params(),
                    "reason",
                    p.reason())));
    for (var row : result) {
      String path = (String) row.get("path");
      String visibility =
          path.startsWith("/api/admin/") || path.startsWith("/admin/")
              ? "ROLE_ADMIN"
              : path.startsWith("/api/studio/") || path.equals("/ai-agent-studio")
                  ? "PROJECT_GRANT"
                  : path.startsWith("/api/open-community/posts")
                          || path.startsWith("/community/posts/")
                      ? "DATA_DEPENDENT"
                      : path.startsWith("/api/chart/")
                              || path.startsWith("/api/academies/")
                              || path.startsWith("/api/analyzes")
                              || Set.of(
                                      "/",
                                      "/home",
                                      "/chart",
                                      "/wiki",
                                      "/newstart",
                                      "/admissions/guide",
                                      "/faq",
                                      "/privacy",
                                      "/academies",
                                      "/academy-posts")
                                  .contains(path)
                          ? "PUBLIC"
                          : Set.of(
                                      "/api/wiki/home-feed",
                                      "/api/wiki/popular-downloads",
                                      "/api/wiki/rounds",
                                      "/api/wiki/rounds/current")
                                  .contains(path)
                              ? "PUBLIC_METADATA_ONLY"
                              : path.startsWith("/api/")
                                      || path.startsWith("/academycraft/")
                                      || path.startsWith("/problem-generator/")
                                      || path.startsWith("/wiki/")
                                      || path.startsWith("/newstart/")
                                  ? "MEMBER_SERVICE_POLICY"
                                  : "SERVER_POLICY";
      row.put("visibility", visibility);
      if (screens.has(path)) row.put("screenSources", screens.get(path));
      row.put(
          "authorizationSource",
          "SecurityConfig + existing controller/service/tenant checks (evaluated on every read)");
    }
    result.sort(Comparator.comparing(r -> (String) r.get("path")));
    return result;
  }

  public Policy policy(String id) {
    if (id == null || !id.startsWith("GET ")) throw McpOAuth.error(400, "unknown_endpoint");
    String path = id.substring(4);
    var p =
        policies.stream()
            .filter(v -> v.path().equals(path))
            .findFirst()
            .orElseThrow(() -> McpOAuth.error(403, "endpoint_not_read_only"));
    if (p.proxy() == null
        && inventory().stream()
            .noneMatch(r -> r.get("id").equals(id) && Boolean.TRUE.equals(r.get("readable"))))
      throw McpOAuth.error(404, "endpoint_not_registered");
    return p;
  }

  public List<String> params(Policy p) {
    if (p.params() != null) return p.params();
    return inventory().stream()
        .filter(r -> r.get("id").equals("GET " + p.path()))
        .findFirst()
        .map(r -> (List<String>) r.get("params"))
        .orElse(List.of());
  }

  public String target(Policy p, JsonNode pathParams, JsonNode query) {
    String path = p.path();
    Matcher matcher = Pattern.compile("\\{([^}:]+)(?::[^}]+)?}").matcher(path);
    StringBuffer out = new StringBuffer();
    Set<String> used = new HashSet<>();
    while (matcher.find()) {
      String key = matcher.group(1);
      String v = pathParams.path(key).asText("");
      // No encoded delimiters, traversal, matrix params or absolute targets, even after decoding.
      if (!v.matches("[\\p{L}\\p{N}_-]{1,100}"))
        throw McpOAuth.error(400, "invalid_path_parameter:" + key);
      matcher.appendReplacement(
          out,
          Matcher.quoteReplacement(
              java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8)));
      used.add(key);
    }
    matcher.appendTail(out);
    if (pathParams.isObject())
      pathParams
          .properties()
          .forEach(
              e -> {
                if (!used.contains(e.getKey())) throw McpOAuth.error(400, "unknown_path_parameter");
              });
    List<String> allowed = params(p);
    List<String> values = new ArrayList<>();
    if (query.isObject())
      query
          .properties()
          .forEach(
              e -> {
                String k = e.getKey(), v = e.getValue().asText("");
                if (!e.getValue().isTextual()) throw McpOAuth.error(400, "invalid_query_parameter");
                if (!allowed.contains(k) || v.length() > 500 || v.chars().anyMatch(c -> c < 32))
                  throw McpOAuth.error(400, "invalid_query_parameter");
                if (Set.of("size", "limit").contains(k)) {
                  try {
                    int maximum =
                        p.path().equals("/api/studio/files/{id}/document") && k.equals("limit")
                            ? 20000
                            : 100;
                    if (Integer.parseInt(v) > maximum || Integer.parseInt(v) < 1)
                      throw McpOAuth.error(400, "query_limit");
                  } catch (NumberFormatException ex) {
                    throw McpOAuth.error(400, "query_limit");
                  }
                }
                values.add(McpOAuth.encode(k) + "=" + McpOAuth.encode(v));
              });
    return out + (values.isEmpty() ? "" : "?" + String.join("&", values));
  }
}
