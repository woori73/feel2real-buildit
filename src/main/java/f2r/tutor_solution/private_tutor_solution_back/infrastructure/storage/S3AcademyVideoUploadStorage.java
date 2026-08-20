package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.file.storage-type", havingValue = "s3")
public class S3AcademyVideoUploadStorage
    implements AcademyVideoUploadStorage, AcademyVideoWorkerStorage {
  private static final String ATTEMPT_PREFIX = "academy-video/attempts/";
  private final AcademyVideoUploadProperties properties;

  public S3AcademyVideoUploadStorage(AcademyVideoUploadProperties properties) {
    this.properties = properties;
  }

  @Value("${app.s3.bucket}")
  private String bucket;
  @Value("${app.s3.region}")
  private String region;
  @Value("${app.s3.key-prefix:}")
  private String keyPrefix;

  private S3Client s3;
  private S3Presigner presigner;

  @PostConstruct
  void initialize() {
    Region awsRegion = Region.of(region);
    s3 = S3Client.builder().region(awsRegion).build();
    presigner = S3Presigner.builder().region(awsRegion).build();
  }

  @PreDestroy
  void close() {
    if (presigner != null) presigner.close();
    if (s3 != null) s3.close();
  }

  @Override
  public UploadSession initiate(String sessionId, long declaredSizeBytes, int expectedPartCount) {
    String prefix = keyPrefix == null || keyPrefix.isBlank() ? "" : keyPrefix + "/";
    String key = prefix + "academy-video/staging/" + sessionId + "/raw";
    try {
      String uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
          .bucket(bucket).key(key).build()).uploadId();
      List<String> urls = new ArrayList<>(expectedPartCount);
      for (int part = 1; part <= expectedPartCount; part++) {
        UploadPartRequest request = UploadPartRequest.builder()
            .bucket(bucket).key(key).uploadId(uploadId).partNumber(part).build();
        PresignedUploadPartRequest signed = presigner.presignUploadPart(
            UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMillis(properties.getUploadDeadlineMs()))
                .uploadPartRequest(request)
                .build());
        urls.add(signed.url().toExternalForm());
      }
      return new UploadSession(key, uploadId, List.copyOf(urls));
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_INIT_FAILED");
    }
  }

  @Override
  public String uploadLocalPart(
      String sessionId, String uploadId, int partNumber, InputStream body, long contentLength) {
    throw new AcademyVideoStorageException("LOCAL_PART_ENDPOINT_DISABLED");
  }

  @Override
  public StorageState inspect(String stagingKey, String uploadId, Duration remaining) {
    long deadlineNanos = deadlineNanos(remaining);
    boolean multipart = false;
    boolean object = false;
    try {
      s3.listParts(ListPartsRequest.builder()
          .bucket(bucket).key(stagingKey).uploadId(uploadId)
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build());
      multipart = true;
    } catch (NoSuchUploadException ignored) {
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_INSPECT_FAILED");
    }
    try {
      s3.headObject(HeadObjectRequest.builder()
          .bucket(bucket).key(stagingKey)
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build());
      object = true;
    } catch (NoSuchKeyException ignored) {
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw new AcademyVideoStorageException("S3_INSPECT_FAILED");
      }
    }
    return new StorageState(multipart, object);
  }

  @Override
  public CompletedObject complete(
      String stagingKey, String uploadId, List<Part> orderedParts, Duration remaining) {
    long deadlineNanos = deadlineNanos(remaining);
    try {
      var listed = s3.listParts(ListPartsRequest.builder()
          .bucket(bucket).key(stagingKey).uploadId(uploadId)
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build()).parts();
      if (listed.size() != orderedParts.size()) {
        throw new AcademyVideoStorageException("MANIFEST_MISMATCH");
      }
      List<CompletedPart> parts = new ArrayList<>(orderedParts.size());
      for (int i = 0; i < orderedParts.size(); i++) {
        Part expected = orderedParts.get(i);
        var actual = listed.get(i);
        if (actual.partNumber() != expected.partNumber()
            || !normalizeEtag(actual.eTag()).equals(normalizeEtag(expected.eTag()))) {
          throw new AcademyVideoStorageException("MANIFEST_MISMATCH");
        }
        parts.add(CompletedPart.builder()
            .partNumber(expected.partNumber()).eTag(expected.eTag()).build());
      }
      var response = s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
          .bucket(bucket).key(stagingKey).uploadId(uploadId)
          .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build());
      ObjectMetadata metadata = head(stagingKey, requireRemaining(deadlineNanos));
      return new CompletedObject(
          response.eTag() != null ? response.eTag() : metadata.eTag(), metadata.sizeBytes());
    } catch (AcademyVideoStorageException e) {
      throw e;
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_COMPLETE_FAILED");
    }
  }

  @Override
  public ObjectMetadata head(String stagingKey, Duration remaining) {
    long deadlineNanos = deadlineNanos(remaining);
    try {
      var response = s3.headObject(HeadObjectRequest.builder()
          .bucket(bucket).key(stagingKey)
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build());
      return new ObjectMetadata(response.eTag(), response.contentLength());
    } catch (NoSuchKeyException e) {
      throw new AcademyVideoStorageException("OBJECT_MISSING");
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new AcademyVideoStorageException("OBJECT_MISSING");
      }
      throw new AcademyVideoStorageException("S3_HEAD_FAILED");
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_HEAD_FAILED");
    }
  }

  @Override
  public InputStream open(String stagingKey, Duration remaining) throws IOException {
    long deadlineNanos = deadlineNanos(remaining);
    try {
      ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder()
          .bucket(bucket).key(stagingKey)
          .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
          .build());
      return response;
    } catch (SdkException e) {
      throw new IOException("S3_READ_FAILED");
    }
  }

  @Override
  public void abort(String stagingKey, String uploadId) {
    try {
      abortForCleanup(stagingKey, uploadId);
    } catch (AcademyVideoStorageException ignored) {
      // Request-path compensation is best effort; the durable cleanup outbox retries strictly.
    }
  }

  @Override
  public void abortForCleanup(String stagingKey, String uploadId) {
    try {
      s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
          .bucket(bucket).key(stagingKey).uploadId(uploadId).build());
    } catch (NoSuchUploadException ignored) {
      // Confirmed absence is idempotent success.
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw new AcademyVideoStorageException("S3_ABORT_FAILED");
      }
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_ABORT_FAILED");
    }
  }

  @Override
  public void materialize(String key, java.nio.file.Path target, Duration remaining)
      throws IOException {
    long deadlineNanos = deadlineNanos(remaining);
    try {
      s3.getObject(
          GetObjectRequest.builder().bucket(bucket).key(key)
              .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
              .build(),
          target);
    } catch (NoSuchKeyException e) {
      throw new AcademyVideoStorageException("OBJECT_MISSING");
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new AcademyVideoStorageException("OBJECT_MISSING");
      }
      throw new IOException("S3_READ_FAILED", e);
    } catch (SdkException e) {
      throw new IOException("S3_READ_FAILED", e);
    }
  }

  @Override
  public String attemptKey(Long jobId, int attempt, String leaseToken, String filename) {
    return physicalAttemptKey(AcademyVideoWorkerStorage.super.attemptKey(
        jobId, attempt, leaseToken, filename));
  }

  @Override
  public StoredObject upload(
      String key, java.nio.file.Path source, String mediaType, Duration remaining)
      throws IOException {
    long deadlineNanos = deadlineNanos(remaining);
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(mediaType)
              .overrideConfiguration(c -> c.apiCallTimeout(requireRemaining(deadlineNanos)))
              .build(),
          RequestBody.fromFile(source));
      return new StoredObject(java.nio.file.Files.size(source));
    } catch (SdkException e) {
      throw new IOException("S3_UPLOAD_FAILED", e);
    }
  }

  @Override
  public StoredObject inspectWorkerObject(String key, Duration remaining) {
    ObjectMetadata metadata = head(key, remaining);
    return new StoredObject(metadata.sizeBytes());
  }

  @Override
  public void deleteWorkerObject(String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucket).key(physicalAttemptKey(key)).build());
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_DELETE_FAILED");
    }
  }

  @Override
  public List<StoredKey> listWorkerObjects(
      String deterministicPrefix, Instant olderThan, int limit) {
    if (olderThan == null || limit < 1 || limit > 1_000) {
      throw new AcademyVideoStorageException("INVALID_RECONCILIATION_REQUEST");
    }
    String actualPrefix = deterministicPrefix.equals("staging/")
        ? (keyPrefix == null || keyPrefix.isBlank() ? "" : keyPrefix + "/")
            + "academy-video/staging/"
        : physicalAttemptKey(deterministicPrefix);
    try {
      return s3.listObjectsV2(ListObjectsV2Request.builder()
              .bucket(bucket).prefix(actualPrefix).maxKeys(limit).build())
          .contents().stream()
          .filter(object -> object.lastModified() != null
              && object.lastModified().isBefore(olderThan))
          .map(object -> new StoredKey(object.key(), object.lastModified()))
          .limit(limit)
          .toList();
    } catch (SdkException e) {
      throw new AcademyVideoStorageException("S3_LIST_FAILED");
    }
  }

  private static String normalizeEtag(String value) {
    return value == null ? "" : value.replace("\"", "").trim();
  }

  private String physicalAttemptKey(String key) {
    if (!key.startsWith(ATTEMPT_PREFIX) || keyPrefix == null || keyPrefix.isBlank()) {
      return key;
    }
    return keyPrefix + "/" + key;
  }

  private static long deadlineNanos(Duration remaining) {
    if (remaining == null || remaining.isNegative() || remaining.isZero()) {
      throw new AcademyVideoStorageException("DEADLINE_EXCEEDED");
    }
    try {
      return Math.addExact(System.nanoTime(), remaining.toNanos());
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static Duration requireRemaining(long deadlineNanos) {
    long nanos = deadlineNanos - System.nanoTime();
    if (nanos <= 0) throw new AcademyVideoStorageException("DEADLINE_EXCEEDED");
    return Duration.ofNanos(nanos);
  }
}
