package f2r.tutor_solution.private_tutor_solution_back.domain.sns.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity.SnsReferenceImage;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.repository.SnsReferenceImageRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 생성 이미지·참고 이미지 파일 읽기/저장. 생성 이미지는 기존 FileStorage(운영 S3)에 둔다. */
@Service
public class SnsMediaService {

  /** FileStorage의 사용자 폴더 대신 쓰는 고정 폴더 번호(시스템 생성물). */
  public static final long STORAGE_OWNER_ID = 0L;
  public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
  private static final String CLASSPATH = "classpath:";

  public record Media(byte[] bytes, String contentType) {}

  private final FileStorage storage;
  private final SnsPostRepository posts;
  private final SnsReferenceImageRepository references;

  public SnsMediaService(FileStorage storage, SnsPostRepository posts, SnsReferenceImageRepository references) {
    this.storage = storage;
    this.posts = posts;
    this.references = references;
  }

  /** 공개 토큰으로 게시물 이미지 읽기(버린 게시물은 내주지 않음). */
  public Optional<Media> postImageByToken(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    return posts.findByMediaToken(token)
        .filter(p -> p.getStatus() != SnsPost.Status.DISCARDED)
        .flatMap(p -> readStorage(p.firstImagePath()));
  }

  public Optional<Media> referenceImage(Long id) {
    return references.findById(id).flatMap(this::read);
  }

  /** GPU 작업자용: 승인된 참고 이미지만. */
  public Optional<Media> approvedReferenceImage(Long id) {
    return references.findById(id).filter(SnsReferenceImage::isApproved).flatMap(this::read);
  }

  public Optional<Media> read(SnsReferenceImage ref) {
    String path = ref.getFilePath();
    if (path.startsWith(CLASSPATH)) {
      ClassPathResource r = new ClassPathResource(path.substring(CLASSPATH.length()));
      if (!r.exists()) return Optional.empty();
      try (InputStream in = r.getInputStream()) {
        return Optional.of(new Media(in.readAllBytes(), ref.getContentType()));
      } catch (IOException e) {
        return Optional.empty();
      }
    }
    return readStorage(path).map(m -> new Media(m.bytes(), ref.getContentType()));
  }

  private Optional<Media> readStorage(String key) {
    if (key == null || key.isBlank()) return Optional.empty();
    try {
      return Optional.of(new Media(storage.readSingleLimited(key, "IMAGE", MAX_IMAGE_BYTES), "image/jpeg"));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  /** GPU 작업자가 보낸 이미지 저장. 인스타그램은 JPEG만 받으므로 JPEG인지 확인한다. */
  public String saveGeneratedJpeg(Long postId, byte[] bytes) throws IOException {
    requireJpeg(bytes);
    return storage.saveImage(STORAGE_OWNER_ID, "sns-post-" + postId, bytes, "jpg");
  }

  public String saveReferenceJpeg(String label, byte[] bytes) throws IOException {
    requireJpeg(bytes);
    return storage.saveImage(STORAGE_OWNER_ID, "sns-reference", bytes, "jpg");
  }

  static void requireJpeg(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 4) throw new IOException("빈 이미지입니다.");
    if (bytes.length > MAX_IMAGE_BYTES) throw new IOException("이미지가 8MB를 넘습니다.");
    boolean jpegMagic = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    if (!jpegMagic) throw new IOException("JPEG 이미지만 받을 수 있습니다.");
    if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new IOException("이미지를 읽을 수 없습니다.");
  }
}
