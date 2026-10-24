package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 로컬 디스크 기반 {@link FileStorage} 구현. {@code app.file.storage-type=local}일 때 활성화.
 *
 * <p>저장 레이아웃 (기존 동작 그대로):
 * <pre>
 *   {basePath}/{userId}/{uuid}_{filename}.jpg              — IMAGE
 *   {basePath}/{userId}/{uuid}/page_01.jpg, page_02.jpg ... — PDF 페이지들
 *   {basePath}/{userId}/{uuid}_{filename}.{ext}            — UNKNOWN 원본
 * </pre>
 * source.storage_key에는 위의 절대 경로가 그대로 들어간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

  @Value("${app.file.upload-dir}")
  private String basePath;

  @Override
  public String saveImage(Long userId, String filenameHint, byte[] bytes, String extension)
      throws IOException {
    Path userDir = userDir(userId);
    String filename = UUID.randomUUID() + "_" + stripExtension(sanitize(filenameHint)) + "." + extension;
    Path dest = userDir.resolve(filename);
    Files.write(dest, bytes);
    return dest.toString();
  }

  @Override
  public String savePdfPages(Long userId, List<byte[]> pageJpegBytes) throws IOException {
    Path userDir = userDir(userId);
    Path pageDir = userDir.resolve(UUID.randomUUID().toString());
    Files.createDirectories(pageDir);
    for (int i = 0; i < pageJpegBytes.size(); i++) {
      String pageName = String.format("page_%02d.jpg", i + 1);
      try (OutputStream out = Files.newOutputStream(pageDir.resolve(pageName))) {
        out.write(pageJpegBytes.get(i));
      }
    }
    return pageDir.toString();
  }

  @Override
  public String saveRaw(Long userId, String filenameHint, InputStream in, long size) throws IOException {
    Path userDir = userDir(userId);
    String filename = UUID.randomUUID() + "_" + sanitize(filenameHint);
    Path dest = userDir.resolve(filename);
    Files.copy(in, dest);
    return dest.toString();
  }

  @Override
  public List<byte[]> readAllPages(String storageKey, String fileType) throws IOException {
    if (!"PDF".equals(fileType)) {
      return List.of(Files.readAllBytes(Path.of(storageKey)));
    }
    Path pageDir = Path.of(storageKey);
    List<byte[]> pages = new ArrayList<>();
    try (var stream = Files.list(pageDir)) {
      var sorted = stream
          .filter(p -> p.getFileName().toString().matches("page_\\d+\\.jpg"))
          .sorted()
          .toList();
      for (Path page : sorted) {
        pages.add(Files.readAllBytes(page));
      }
    }
    return pages;
  }

  @Override
  public byte[] readPage(String storageKey, String fileType, int page) throws IOException {
    if (!"PDF".equals(fileType)) {
      return Files.readAllBytes(Path.of(storageKey));
    }
    String pageName = String.format("page_%02d.jpg", page);
    Path pagePath = Path.of(storageKey, pageName);
    if (!Files.exists(pagePath)) {
      throw new IOException("페이지를 찾을 수 없습니다: " + pageName);
    }
    return Files.readAllBytes(pagePath);
  }

  @Override
  public byte[] readSingleLimited(String storageKey, String fileType, long maxBytes) throws IOException {
    int boundedReadLength = boundedReadLength(maxBytes);
    if ("PDF".equals(fileType)) {
      throw new IOException("PDF 묶음은 단일 객체 제한 조회를 지원하지 않습니다.");
    }

    Path path = Path.of(storageKey);
    if (Files.size(path) > maxBytes) {
      throw new StorageReadLimitExceededException(maxBytes);
    }

    byte[] bytes;
    try (InputStream in = Files.newInputStream(path)) {
      bytes = in.readNBytes(boundedReadLength);
    }
    if (bytes.length > maxBytes) {
      throw new StorageReadLimitExceededException(maxBytes);
    }
    return bytes;
  }

  @Override
  public InputStream openRange(String storageKey, long start, long end) throws IOException {
    if (start < 0 || end < start) throw new IOException("invalid range");
    var channel = Files.newByteChannel(Path.of(storageKey));
    try {
      channel.position(start);
    } catch (IOException e) {
      channel.close();
      throw e;
    }
    return java.nio.channels.Channels.newInputStream(channel);
  }

  @Override
  public int pageCount(String storageKey, String fileType) {
    if (!"PDF".equals(fileType)) return 1;
    try (var stream = Files.list(Path.of(storageKey))) {
      long n = stream
          .filter(p -> p.getFileName().toString().matches("page_\\d+\\.jpg"))
          .count();
      return n > 0 ? (int) n : 1;
    } catch (IOException e) {
      log.warn("PDF 페이지 수 계산 실패: reason={}", e.getClass().getSimpleName());
      return 1;
    }
  }

  @Override
  public void delete(String storageKey, String fileType) {
    deleteReported(storageKey, fileType);
  }

  @Override
  public boolean deleteReported(String storageKey, String fileType) {
    if (storageKey == null || storageKey.isBlank()) return true;
    try {
      Path path = Path.of(storageKey);
      if ("PDF".equals(fileType) && Files.isDirectory(path)) {
        boolean complete = true;
        try (var stream = Files.list(path)) {
          for (Path child : stream.toList()) {
            try {
              Files.deleteIfExists(child);
            } catch (IOException e) {
              complete = false;
              log.warn("storage page delete 실패: reason={}", e.getClass().getSimpleName());
            }
          }
        }
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          complete = false;
          log.warn("storage directory delete 실패: reason={}", e.getClass().getSimpleName());
        }
        return complete && !Files.exists(path);
      } else {
        Files.deleteIfExists(path);
        return !Files.exists(path);
      }
    } catch (Exception e) {
      log.warn("storage delete 실패: reason={}", e.getClass().getSimpleName());
      return false;
    }
  }

  private Path userDir(Long userId) throws IOException {
    Path dir = Path.of(basePath, String.valueOf(userId));
    Files.createDirectories(dir);
    return dir;
  }

  private String sanitize(String name) {
    if (name == null) return "file";
    return name.replaceAll("[^a-zA-Z0-9._\\-가-힣]", "_");
  }

  private String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private int boundedReadLength(long maxBytes) {
    if (maxBytes < 0 || maxBytes >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("maxBytes는 0 이상 Integer.MAX_VALUE 미만이어야 합니다.");
    }
    return (int) maxBytes + 1;
  }
}
