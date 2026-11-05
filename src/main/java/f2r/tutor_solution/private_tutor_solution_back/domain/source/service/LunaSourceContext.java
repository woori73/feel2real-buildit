package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reuses stored page images; does not change extraction or retrieval rules. */
@Component
public class LunaSourceContext {
  private final FileStorage fileStorage;
  public LunaSourceContext(FileStorage fileStorage) { this.fileStorage = fileStorage; }

  public List<Map<String, Object>> content(List<Source> sources, String prompt) throws IOException {
    List<Map<String, Object>> parts = new ArrayList<>();
    for (Source source : sources) {
      String key = source.getStorageKey() == null ? "" : source.getStorageKey().toLowerCase(Locale.ROOT);
      String mime = "PDF".equals(source.getFileType()) ? "image/jpeg"
          : key.endsWith(".png") ? "image/png" : key.endsWith(".gif") ? "image/gif"
          : key.endsWith(".webp") ? "image/webp" : "image/jpeg";
      for (byte[] page : fileStorage.readAllPages(source.getStorageKey(), source.getFileType())) {
        parts.add(Map.of("type", "input_image", "image_url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(page)));
      }
    }
    parts.add(Map.of("type", "input_text", "text", prompt));
    return parts;
  }
}
