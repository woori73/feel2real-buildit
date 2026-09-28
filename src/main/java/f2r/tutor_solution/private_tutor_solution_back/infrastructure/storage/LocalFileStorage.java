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
 * <p>저장 레이아웃:
 * <pre>
 *   {basePath}/{userId}/{uuid}_{filename}.jpg              — IMAGE
 *   {basePath}/{userId}/{uuid}/page_01.jpg, page_02.jpg ... — PDF 페이지들
 *   {basePath}/{userId}/{uuid}_{filename}.{ext}            — UNKNOWN 원본
 * </pre>
 * storage_key에는 basePath로부터의 상대 경로가 저장된다.
 * 이렇게 하면 LocalAcademyVideoDeliveryStorage의 resolvePath(asyncRoot/objects/{key})가
 * 같은 베이스 디렉터리(asyncRoot/objects)를 공유할 때 키를 바로 찾을 수 있다.
 * 저장/읽기 내부에서는 절대 경로를 쓰되, 저장 시 반환하는 storage_key는 basePath 기준 상대 경로다.
 * 읽기/삭제 메서드는 상대 경로와 절대 경로를 모두 처리한다.
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
    return toRelative(dest);
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
    return toRelative(pageDir);
  }

  @Override
  public String saveRaw(Long userId, String filenameHint, InputStream in, long size) throws IOException {
    Path userDir = userDir(userId);
    String filename = UUID.randomUUID() + "_" + sanitize(filenameHint);
    Path dest = userDir.resolve(filename);
    Files.copy(in, dest);
    return toRelative(dest);
  }

  @Override
  public List<byte[]> readAllPages(String storageKey, String fileType) throws IOException {
    Path path = resolveStorageKey(storageKey);
    if (!"PDF".equals(fileType)) {
      return List.of(Files.readAllBytes(path));
    }
    Path pageDir = path;
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
    Path path = resolveStorageKey(storageKey);
    if (!"PDF".equals(fileType)) {
      return Files.readAllBytes(path);
    }
    String pageName = String.format("page_%02d.jpg", page);
    Path pagePath = path.resolve(pageName);
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

    Path path = resolveStorageKey(storageKey);
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
  public int pageCount(String storageKey, String fileType) {
    if (!"PDF".equals(fileType)) return 1;
    try {
      Path path = resolveStorageKey(storageKey);
      try (var stream = Files.list(path)) {
        long n = stream
            .filter(p -> p.getFileName().toString().matches("page_\\d+\\.jpg"))
            .count();
        return n > 0 ? (int) n : 1;
      }
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
      Path path = resolveStorageKey(storageKey);
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
        Files.deleteIfExists(path);
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

  private Path baseDir() {
    return Path.of(basePath).toAbsolutePath().normalize();
  }

  private Path userDir(Long userId) throws IOException {
    Path dir = baseDir().resolve(String.valueOf(userId));
    Files.createDirectories(dir);
    return dir;
  }

  private String toRelative(Path absolutePath) {
    Path base = baseDir();
    try {
      return base.relativize(absolutePath).toString();
    } catch (IllegalArgumentException e) {
      // 이미 상대 경로이거나 다른 볼륨인 경우 원본 반환
      return absolutePath.toString();
    }
  }

  private Path resolveStorageKey(String storageKey) {
    Path keyPath = Path.of(storageKey);
    if (keyPath.isAbsolute()) {
      return keyPath;
    }
    return baseDir().resolve(storageKey).normalize();
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