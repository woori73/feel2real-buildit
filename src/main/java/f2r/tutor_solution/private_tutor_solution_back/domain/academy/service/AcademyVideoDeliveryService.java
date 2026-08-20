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
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect.location())
            .cacheControl(CacheControl.noStore())
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
        .cacheControl(CacheControl.noStore())
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
        .cacheControl(CacheControl.noStore())
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
