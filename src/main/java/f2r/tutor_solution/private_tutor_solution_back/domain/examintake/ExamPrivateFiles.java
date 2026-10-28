package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamPrivateFiles {
  static final long MAX_BYTES = 10L * 1024 * 1024;
  final Path root;
  private final String scanner;

  public record Prepared(
      String name, String type, long size, String hash, String storage, String scanState) {}

  public ExamPrivateFiles(
      @Value("${app.exam-intake.private-directory:}") String directory,
      @Value("${app.exam-intake.scanner-executable:}") String scanner)
      throws IOException {
    if (directory.isBlank())
      throw new IllegalStateException("exam-intake private-directory is required");
    root = Path.of(directory).toAbsolutePath().normalize();
    this.scanner = scanner;
    if (root.toString().contains("/static/")
        || root.endsWith("static")
        || root.toString().contains("agent-media")
        || root.equals(Path.of("/")))
      throw new IllegalStateException(
          "Private exam storage must be separate from public/media storage");
    for (Path p = root; p != null; p = p.getParent())
      if (Files.isSymbolicLink(p))
        throw new IllegalStateException("Private storage cannot follow symlinks");
    Files.createDirectories(root);
    Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
  }

  Prepared prepare(MultipartFile upload) throws IOException {
    if (upload == null || upload.isEmpty() || upload.getSize() > MAX_BYTES)
      throw error(400, "파일은 1바이트 이상 10MiB 이하여야 합니다.");
    String name = text(upload.getOriginalFilename(), "파일명", 1, 160);
    if (name.contains("/") || name.contains("\\") || name.contains("\r") || name.contains("\n"))
      throw error(400, "파일명을 확인해 주세요.");
    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    String type =
        switch (ext) {
          case "pdf" -> "application/pdf";
          case "jpg", "jpeg" -> "image/jpeg";
          case "png" -> "image/png";
          default -> throw error(400, "PDF/JPEG/PNG만 제출할 수 있습니다.");
        };
    if (!type.equals(upload.getContentType())) throw error(400, "파일 형식과 확장자가 일치하지 않습니다.");
    Path temp = Files.createTempFile(root, "upload-", ".tmp");
    boolean retained = false;
    try {
      Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
      try (var in = upload.getInputStream();
          var out = Files.newOutputStream(temp)) {
        long size = 0;
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
          size += n;
          if (size > MAX_BYTES) throw error(400, "파일 용량 제한을 초과했습니다.");
          out.write(buf, 0, n);
        }
      }
      validate(temp, type);
      String scan = scan(temp);
      if ("INFECTED".equals(scan)) throw error(400, "안전하지 않은 파일로 판정되어 저장하지 않았습니다.");
      String sha = hash(Files.readAllBytes(temp));
      String storage = UUID.randomUUID() + ".bin";
      long size = Files.size(temp);
      Files.move(temp, root.resolve(storage), StandardCopyOption.ATOMIC_MOVE);
      retained = true;
      return new Prepared(name, type, size, sha, storage, scan);
    } finally {
      if (!retained) Files.deleteIfExists(temp);
    }
  }

  private void validate(Path file, String type) throws IOException {
    try {
      byte[] signature;
      try (var in = Files.newInputStream(file)) {
        signature = in.readNBytes(8);
      }
      if (type.equals("application/pdf")) {
        if (signature.length < 5
            || !new String(signature, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                .equals("%PDF-")) throw error(400, "유효한 PDF가 아닙니다.");
        try (var pdf = Loader.loadPDF(file.toFile())) {
          if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 30)
            throw error(400, "암호화 PDF 또는 30쪽 초과 PDF는 제출할 수 없습니다.");
          inspect(
              pdf.getDocumentCatalog().getCOSObject(),
              Collections.newSetFromMap(new IdentityHashMap<>()),
              new int[] {0});
        }
      } else {
        boolean png =
            signature.length == 8
                && Arrays.equals(signature, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        boolean jpg =
            signature.length >= 3
                && (signature[0] & 255) == 255
                && (signature[1] & 255) == 216
                && (signature[2] & 255) == 255;
        if (type.equals("image/png") ? !png : !jpg) throw error(400, "파일 서명이 일치하지 않습니다.");
        try (var input = ImageIO.createImageInputStream(file.toFile())) {
          var readers = ImageIO.getImageReaders(input);
          if (!readers.hasNext()) throw error(400, "이미지를 읽을 수 없습니다.");
          var reader = readers.next();
          try {
            reader.setInput(input);
            long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            if (pixels < 1 || pixels > 20_000_000) throw error(400, "이미지는 2천만 픽셀 이하여야 합니다.");
            if (reader.read(0) == null) throw error(400, "이미지 디코딩에 실패했습니다.");
          } finally {
            reader.dispose();
          }
        }
      }
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw error(400, "파일을 읽을 수 없습니다. 손상·암호화 여부를 확인해 주세요.");
    }
  }

  private void inspect(COSBase object, Set<COSBase> seen, int[] count) {
    Deque<COSBase> pending = new ArrayDeque<>();
    if (object != null) pending.push(object);
    while (!pending.isEmpty()) {
      COSBase current = pending.pop();
      if (!seen.add(current)) continue;
      if (++count[0] > 10000) throw error(400, "PDF 구조가 너무 복잡합니다.");
      if (current instanceof COSObject ref) {
        if (ref.getObject() != null) pending.push(ref.getObject());
      } else if (current instanceof COSDictionary dict) {
        for (var entry : dict.entrySet()) {
          if (Set.of(
                  "AA",
                  "OpenAction",
                  "JS",
                  "JavaScript",
                  "EmbeddedFiles",
                  "RichMedia",
                  "Launch",
                  "XFA",
                  "AcroForm")
              .contains(entry.getKey().getName()))
            throw error(400, "스크립트·양식·첨부가 있는 PDF는 제출할 수 없습니다.");
          if (entry.getValue() instanceof COSName name
              && Set.of("JavaScript", "Launch", "RichMedia").contains(name.getName()))
            throw error(400, "활성 콘텐츠 PDF는 제출할 수 없습니다.");
          if (entry.getValue() != null) pending.push(entry.getValue());
        }
      } else if (current instanceof COSArray arr)
        for (COSBase item : arr) if (item != null) pending.push(item);
    }
  }

  String scan(Path file) {
    if (scanner.isBlank()) return "QUARANTINED";
    Process process = null;
    try {
      if (!Path.of(scanner).isAbsolute() || !Files.isExecutable(Path.of(scanner)))
        return "QUARANTINED";
      process =
          new ProcessBuilder(scanner, "--no-summary", "--", file.toString())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (!process.waitFor(30, TimeUnit.SECONDS)) return "QUARANTINED";
      return process.exitValue() == 0
          ? "CLEAN"
          : process.exitValue() == 1 ? "INFECTED" : "QUARANTINED";
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return "QUARANTINED";
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }

  Path path(String name) {
    if (name == null || !name.matches("[a-f0-9-]{36}\\.bin")) throw error(404, "파일을 찾을 수 없습니다.");
    Path p = root.resolve(name);
    if (Files.isSymbolicLink(p)) throw error(404, "파일을 찾을 수 없습니다.");
    return p;
  }

  void discard(String storage) throws IOException {
    Files.deleteIfExists(path(storage));
  }
}
