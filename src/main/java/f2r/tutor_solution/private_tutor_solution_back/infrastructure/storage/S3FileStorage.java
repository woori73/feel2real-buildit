package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 기반 {@link FileStorage} 구현. {@code app.file.storage-type=s3}일 때 활성화.
 *
 * <p>인증: EC2의 Instance Profile (IAM 역할 {@code f2r-ec2-role})을 default credential provider chain이 자동으로 사용한다.
 * 로컬에서 테스트하려면 {@code ~/.aws/credentials} 또는 {@code AWS_ACCESS_KEY_ID/SECRET_ACCESS_KEY} 환경변수.
 *
 * <p>키 레이아웃:
 * <pre>
 *   {prefix}/users/{userId}/{uuid}_{filename}.jpg              — IMAGE (storage_key = 그대로 객체키)
 *   {prefix}/users/{userId}/{uuid}/page_01.jpg, page_02.jpg ... — PDF (storage_key = "{prefix}/users/{userId}/{uuid}")
 *   {prefix}/users/{userId}/{uuid}_{filename}                  — UNKNOWN (storage_key = 객체키)
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.file.storage-type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

  @Value("${app.s3.bucket}")
  private String bucket;

  @Value("${app.s3.region}")
  private String region;

  /** 한 버킷을 여러 환경(dev/staging/prd)이 공유할 때 분리용 prefix. 빈 문자열 허용. */
  @Value("${app.s3.key-prefix:}")
  private String keyPrefix;

  private S3Client s3;

  @PostConstruct
  void init() {
    this.s3 = S3Client.builder().region(Region.of(region)).build();
    log.info("S3FileStorage initialized: bucket={}, region={}, keyPrefix='{}'", bucket, region, keyPrefix);
  }

  @PreDestroy
  void close() {
    if (s3 != null) s3.close();
  }

  @Override
  public String saveImage(Long userId, String filenameHint, byte[] bytes, String extension) {
    String key = userPrefix(userId)
        + UUID.randomUUID() + "_" + stripExtension(sanitize(filenameHint)) + "." + extension;
    // 오브젝트 Content-Type도 실제 포맷을 따라간다. 고정 image/jpeg면 GIF가 JPEG로 내려간다.
    putObject(key, bytes, ImageSanitizer.contentTypeForStorageKey(key));
    return key;
  }

  @Override
  public String savePdfPages(Long userId, List<byte[]> pageJpegBytes) {
    String pagePrefix = userPrefix(userId) + UUID.randomUUID();
    for (int i = 0; i < pageJpegBytes.size(); i++) {
      String pageName = String.format("page_%02d.jpg", i + 1);
      putObject(pagePrefix + "/" + pageName, pageJpegBytes.get(i), "image/jpeg");
    }
    return pagePrefix;
  }

  @Override
  public String saveRaw(Long userId, String filenameHint, InputStream in, long size) throws IOException {
    String key = userPrefix(userId) + UUID.randomUUID() + "_" + sanitize(filenameHint);
    // S3 SDK는 InputStream + 길이 형태 RequestBody를 제공. 길이를 명시해야 chunked transfer가 안 일어난다.
    PutObjectRequest req = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build();
    s3.putObject(req, RequestBody.fromInputStream(in, size));
    return key;
  }

  @Override
  public List<byte[]> readAllPages(String storageKey, String fileType) throws IOException {
    if (!"PDF".equals(fileType)) {
      return List.of(getObjectBytes(storageKey));
    }
    // PDF: storageKey는 prefix. 그 아래 page_*.jpg를 정렬해서 반환.
    List<S3Object> objects = listPageObjects(storageKey);
    List<byte[]> pages = new ArrayList<>(objects.size());
    for (S3Object obj : objects) {
      pages.add(getObjectBytes(obj.key()));
    }
    return pages;
  }

  @Override
  public byte[] readPage(String storageKey, String fileType, int page) throws IOException {
    if (!"PDF".equals(fileType)) {
      return getObjectBytes(storageKey);
    }
    String pageKey = storageKey + "/" + String.format("page_%02d.jpg", page);
    return getObjectBytes(pageKey);
  }

  @Override
  public byte[] readSingleLimited(String storageKey, String fileType, long maxBytes) throws IOException {
    int boundedReadLength = boundedReadLength(maxBytes);
    if ("PDF".equals(fileType)) {
      throw new IOException("PDF 묶음은 단일 객체 제한 조회를 지원하지 않습니다.");
    }

    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucket)
        .key(storageKey)
        .build();
    try (ResponseInputStream<GetObjectResponse> in = s3.getObject(request)) {
      Long contentLength = in.response().contentLength();
      if (contentLength != null && contentLength > maxBytes) {
        throw new StorageReadLimitExceededException(maxBytes);
      }
      byte[] bytes = in.readNBytes(boundedReadLength);
      if (bytes.length > maxBytes) {
        throw new StorageReadLimitExceededException(maxBytes);
      }
      return bytes;
    } catch (StorageReadLimitExceededException e) {
      throw e;
    } catch (SdkException | IOException e) {
      // AWS SDK exception messages/causes can contain bucket and object key details. Normalize at
      // the storage boundary and deliberately do not retain the cause.
      throw new StorageReadException();
    }
  }

  @Override
  public int pageCount(String storageKey, String fileType) {
    if (!"PDF".equals(fileType)) return 1;
    try {
      int count = listPageObjects(storageKey).size();
      return count > 0 ? count : 1;
    } catch (Exception e) {
      log.warn("S3 PDF 페이지 수 계산 실패: reason={}", e.getClass().getSimpleName());
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
      if ("PDF".equals(fileType)) {
        boolean complete = true;
        for (S3Object object : listPageObjects(storageKey)) {
          try {
            s3.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(object.key())
                .build());
          } catch (Exception e) {
            complete = false;
            log.warn("S3 page delete 실패: reason={}", e.getClass().getSimpleName());
          }
        }
        return complete;
      } else {
        s3.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
            .bucket(bucket).key(storageKey).build());
        return true;
      }
    } catch (Exception e) {
      log.warn("S3 delete 실패: reason={}", e.getClass().getSimpleName());
      return false;
    }
  }

  @Override
  public InputStream openRange(String storageKey, long start, long end) throws IOException {
    if (start < 0 || end < start) throw new IOException("invalid range");
    try {
      return s3.getObject(
          GetObjectRequest.builder()
              .bucket(bucket)
              .key(storageKey)
              .range("bytes=" + start + "-" + end)
              .build());
    } catch (SdkException e) {
      // 버킷·키가 메시지에 섞이지 않도록 원인 없이 정규화한다(readSingleLimited와 같은 경계).
      throw new StorageReadException();
    }
  }

  // ---- helpers ----

  private void putObject(String key, byte[] bytes, String contentType) {
    PutObjectRequest req = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .contentLength((long) bytes.length)
        .build();
    s3.putObject(req, RequestBody.fromBytes(bytes));
  }

  private byte[] getObjectBytes(String key) throws IOException {
    GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
    try (ResponseInputStream<GetObjectResponse> in = s3.getObject(req)) {
      return in.readAllBytes();
    }
  }

  /** prefix 아래 page_*.jpg 객체들을 키 이름 사전순으로 반환. */
  private List<S3Object> listPageObjects(String prefix) {
    String listPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
    ListObjectsV2Request req = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(listPrefix)
        .build();
    ListObjectsV2Response resp = s3.listObjectsV2(req);
    return resp.contents().stream()
        .filter(o -> o.key().substring(listPrefix.length()).matches("page_\\d+\\.jpg"))
        .sorted(Comparator.comparing(S3Object::key))
        .toList();
  }

  private String userPrefix(Long userId) {
    String pfx = (keyPrefix == null || keyPrefix.isBlank()) ? "" : keyPrefix + "/";
    return pfx + "users/" + userId + "/";
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
