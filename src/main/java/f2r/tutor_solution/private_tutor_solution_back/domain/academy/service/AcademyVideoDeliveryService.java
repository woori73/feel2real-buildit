package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyVideo;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoActiveSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VideoLifecycleStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage.LocalTarget;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage.RedirectTarget;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;

@Service
public class AcademyVideoDeliveryService {
  private static final int COPY_BUFFER_BYTES = 64 * 1024;

  /**
   * 정규화된 영상 바이트는 videoId 당 불변이다(재업로드는 새 id 를 만든다). 그래서 브라우저가
   * 오래 캐시해도 안전하고, {@code <video preload="metadata">} 로 받아둔 앞부분을 나중의 첫
   * 클릭 재생이 그대로 재사용한다 → "첫 영상 첫 클릭 버퍼링" 제거. shared proxy 는 피하려고
   * private 로 둔다(발행 학원의 공개 콘텐츠지만 URL 이 슬러그별이라 공유 이득이 작다).
   */
  private static final CacheControl IMMUTABLE_MEDIA =
      CacheControl.maxAge(Duration.ofDays(7)).cachePrivate().immutable();

  private final AcademyService academyService;
  private final AcademyVideoRepository videoRepository;
  private final AcademyVideoDeliveryStorage storage;
  private final Duration signedUrlTtl;

  public AcademyVideoDeliveryService(
      AcademyService academyService,
      AcademyVideoRepository videoRepository,
      AcademyVideoDeliveryStorage storage,
      AcademyVideoUploadProperties properties) {
    this.academyService = academyService;
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.signedUrlTtl = Duration.ofMillis(properties.getDeliverySignedUrlTtlMs());
  }

  public ResponseEntity<StreamingResponseBody> get(String slug, Long videoId, String rangeHeader) {
    ActiveVideo source = resolveAuthorizedSource(slug, videoId);
    try {
      ByteRange range = parseRange(rangeHeader, source.length());
      var target = storage.resolve(source.key(), source.mediaType(), signedUrlTtl);
      if (target instanceof RedirectTarget redirect) {
        // 302 자체를 signed-URL 유효시간 안에서 재사용하게 한다 — 그래야 preload 와 첫 재생이
        // "같은" presigned URL 을 받아 브라우저 미디어 캐시(URL 키)가 적중한다. 매 클릭마다
        // 새 URL 을 발급하면 캐시가 미스되고 S3 로 콜드 재연결이 일어난다("첫 클릭 버퍼링").
        Duration redirectCache = signedUrlTtl.minusSeconds(60);
        if (redirectCache.isZero() || redirectCache.isNegative()) redirectCache = Duration.ofSeconds(1);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect.location())
            .cacheControl(CacheControl.maxAge(redirectCache).cachePrivate())
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .build();
      }

      LocalTarget local = (LocalTarget) target;
      if (local.length() != source.length()) {
        closeQuietly(local.channel());
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "영상 메타데이터가 일치하지 않습니다.");
      }
      if (range == null) {
        return stream(local.channel(), source, 0, source.length(), HttpStatus.OK, null);
      }
      return stream(
          local.channel(), source, range.start(), range.length(), HttpStatus.PARTIAL_CONTENT,
          "bytes " + range.start() + "-" + range.end() + "/" + source.length());
    } catch (UnsatisfiableRangeException e) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .cacheControl(CacheControl.noStore())
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_RANGE, "bytes */" + source.length())
          .build();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상 객체를 찾을 수 없습니다.", e);
    }
  }

  public ResponseEntity<Void> head(String slug, Long videoId) {
    ActiveVideo source = resolveAuthorizedSource(slug, videoId);
    return ResponseEntity.ok()
        .cacheControl(IMMUTABLE_MEDIA)
        .contentType(MediaType.parseMediaType(source.mediaType()))
        .contentLength(source.length())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .build();
  }

  private ActiveVideo resolveAuthorizedSource(String slug, Long videoId) {
    var academy = academyService.getPublishedEntity(slug);
    AcademyVideo video = videoRepository
        .findByVideoIdAndAcademy_AcademyId(videoId, academy.getAcademyId())
        .filter(found -> found.getLifecycleStatus() == VideoLifecycleStatus.READY)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));
    if (video.getActiveSource() == VideoActiveSource.RAW) {
      return requireSource(video.getRawStorageKey(), video.getRawMediaType(), video.getRawSizeBytes());
    }
    return requireSource(
        video.getNormalizedStorageKey(), video.getNormalizedMediaType(), video.getNormalizedSizeBytes());
  }

  private ActiveVideo requireSource(String key, String mediaType, Long length) {
    if (key == null || key.isBlank() || mediaType == null || mediaType.isBlank()
        || length == null || length < 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상 소스를 찾을 수 없습니다.");
    }
    return new ActiveVideo(key, mediaType, length);
  }

  private ResponseEntity<StreamingResponseBody> stream(
      SeekableByteChannel channel,
      ActiveVideo source,
      long start,
      long length,
      HttpStatus status,
      String contentRange) {
    StreamingResponseBody body = output -> {
      try (channel) {
        channel.position(start);
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(COPY_BUFFER_BYTES, length));
        long remaining = length;
        while (remaining > 0) {
          buffer.clear();
          buffer.limit((int) Math.min(buffer.capacity(), remaining));
          int read = channel.read(buffer);
          if (read < 0) break;
          output.write(buffer.array(), 0, read);
          remaining -= read;
        }
      }
    };
    var response = ResponseEntity.status(status)
        .cacheControl(IMMUTABLE_MEDIA)
        .contentType(MediaType.parseMediaType(source.mediaType()))
        .contentLength(length)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes");
    if (contentRange != null) response.header(HttpHeaders.CONTENT_RANGE, contentRange);
    return response.body(body);
  }

  private static ByteRange parseRange(String header, long length) {
    if (header == null || header.isBlank()) return null;
    if (!header.startsWith("bytes=") || header.indexOf(',') >= 0) {
      throw new UnsatisfiableRangeException();
    }
    String value = header.substring("bytes=".length()).trim();
    int dash = value.indexOf('-');
    if (dash < 0) throw new UnsatisfiableRangeException();
    try {
      if (dash == 0) {
        long suffix = Long.parseLong(value.substring(1));
        if (suffix <= 0) throw new UnsatisfiableRangeException();
        long start = Math.max(0, length - suffix);
        return new ByteRange(start, length - 1);
      }
      long start = Long.parseLong(value.substring(0, dash));
      if (start < 0 || start >= length) throw new UnsatisfiableRangeException();
      long end = dash == value.length() - 1
          ? length - 1
          : Math.min(Long.parseLong(value.substring(dash + 1)), length - 1);
      if (end < start) throw new UnsatisfiableRangeException();
      return new ByteRange(start, end);
    } catch (NumberFormatException e) {
      throw new UnsatisfiableRangeException();
    }
  }

  private static void closeQuietly(SeekableByteChannel channel) {
    try {
      channel.close();
    } catch (IOException ignored) {
    }
  }

  private record ActiveVideo(String key, String mediaType, long length) {}
  private record ByteRange(long start, long end) {
    long length() {
      return end - start + 1;
    }
  }
  private static final class UnsatisfiableRangeException extends RuntimeException {}
}
