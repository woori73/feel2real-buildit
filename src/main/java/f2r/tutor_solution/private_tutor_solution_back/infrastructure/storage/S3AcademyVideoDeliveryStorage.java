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

  @Override
  public DeliveryTarget resolve(String key, String mediaType, Duration signedUrlTtl) {
    if (key == null || key.isBlank() || signedUrlTtl == null
        || signedUrlTtl.isZero() || signedUrlTtl.isNegative()
        || signedUrlTtl.compareTo(Duration.ofSeconds(30)) > 0) {
      throw new AcademyVideoStorageException("INVALID_DELIVERY_REQUEST");
    }
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .responseContentType(mediaType)
        .responseCacheControl("no-store")
        .build();
    URI location = URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(signedUrlTtl)
            .getObjectRequest(get)
            .build())
        .url().toExternalForm());
    return new RedirectTarget(location);
  }
}
