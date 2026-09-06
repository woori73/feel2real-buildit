package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.file.storage-type", havingValue = "s3")
public class S3AcademyVideoDeliveryStorage implements AcademyVideoDeliveryStorage {
  @Value("${app.s3.bucket}")
  private String bucket;
  @Value("${app.s3.region}")
  private String region;

  private S3Presigner presigner;

  @PostConstruct
  void initialize() {
    presigner = S3Presigner.builder().region(Region.of(region)).build();
  }

  @PreDestroy
  void close() {
    if (presigner != null) presigner.close();
  }

  /**
   * signed URL 유효시간 상한. EC2 instance-profile 임시 자격증명 갱신 주기보다 넉넉히 짧게 둔다
   * (URL 이 자격증명보다 오래 살면 403). 방문자가 페이지를 읽는 동안 preload 로 받은 URL 이
   * 클릭 시점까지 살아 있어야 "첫 클릭 콜드 재연결"이 사라진다(과거 상한 30초는 이 window 를 못 덮었다).
   */
  private static final Duration MAX_SIGNED_URL_TTL = Duration.ofHours(1);

  @Override
  public DeliveryTarget resolve(String key, String mediaType, Duration signedUrlTtl) {
    if (key == null || key.isBlank() || signedUrlTtl == null
        || signedUrlTtl.isZero() || signedUrlTtl.isNegative()
        || signedUrlTtl.compareTo(MAX_SIGNED_URL_TTL) > 0) {
      throw new AcademyVideoStorageException("INVALID_DELIVERY_REQUEST");
    }
    // 정규화된 영상은 key 당 불변이라(재업로드=새 key) 브라우저가 오래 캐시해도 안전하다.
    // no-store 를 쓰면 preload="metadata" 로 받은 앞부분이 버려져 클릭마다 콜드 재다운로드가 된다.
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .responseContentType(mediaType)
        .responseCacheControl("private, max-age=604800, immutable")
        .build();
    URI location = URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(signedUrlTtl)
            .getObjectRequest(get)
            .build())
        .url().toExternalForm());
    return new RedirectTarget(location);
  }
}
