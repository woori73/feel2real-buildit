package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AlbAccessLogParser {
  private final String baseDomainSuffix;

  public AlbAccessLogParser(String baseDomain) {
    this.baseDomainSuffix = "." + baseDomain.toLowerCase(Locale.ROOT);
  }

  public Optional<RequestFact> parseCountableRequest(String line) {
    try {
      List<String> fields = tokenize(line);
      if (fields.size() < 13) return Optional.empty();
      int status = Integer.parseInt(fields.get(8));
      if (status < 200 || status >= 400) return Optional.empty();
      String[] request = fields.get(12).split(" ", 3);
      if (request.length < 2 || !"GET".equals(request[0])) return Optional.empty();
      URI uri = URI.create(request[1]);
      if (!"/".equals(uri.getPath())) return Optional.empty();
      String host = uri.getHost();
      if (host == null) return Optional.empty();
      host = host.toLowerCase(Locale.ROOT);
      if (!host.endsWith(baseDomainSuffix)) return Optional.empty();
      String handle = host.substring(0, host.length() - baseDomainSuffix.length());
      if (handle.isBlank() || handle.contains(".")) return Optional.empty();
      return Optional.of(new RequestFact(handle, Instant.parse(fields.get(1))));
    } catch (IllegalArgumentException | DateTimeParseException ignored) {
      return Optional.empty();
    }
  }

  static List<String> tokenize(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (escaped) {
        current.append(c);
        escaped = false;
      } else if (c == '\\' && quoted) {
        escaped = true;
      } else if (c == '"') {
        quoted = !quoted;
      } else if (Character.isWhitespace(c) && !quoted) {
        if (!current.isEmpty()) {
          fields.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.append(c);
      }
    }
    if (!current.isEmpty()) fields.add(current.toString());
    return fields;
  }

  public record RequestFact(String handle, Instant occurredAt) {}
}
