package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AcademyVideoUploadProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.repository.LmsContentRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage.LocalTarget;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.AcademyVideoDeliveryStorage.RedirectTarget;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * LMS 복습영상 스트리밍(작업지시서 §5). {@code AcademyVideoDeliveryService}와 같은 Range 응답
 * 로직을 그대로 따르되, 인가는 "발행된 Academy"가 아니라 {@link LmsLearnerAccessService}(owner 또는
 * ACTIVE 수강 학생)로 판정한다 — 이 영상은 재원생 전용이라 누구나 볼 수 있으면 안 된다.
 *
 * <p>저장소 delivery 계층({@link AcademyVideoDeliveryStorage})은 storage key를 불투명 문자열로만
 * 다루는 범용 컴포넌트라 그대로 재사용한다(academy_videos 전용 로직이 아니다).
 */
@Service
public class LmsContentDeliveryService {
  private static final int COPY_BUFFER_BYTES = 64 * 1024;
  private static final CacheControl PRIVATE_MEDIA =
      CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();
  private static final String VIDEO_MEDIA_TYPE = "video/mp4";

  private final LmsContentRepository contentRepository;
  private final LmsLearnerAccessService accessService;
  private final AcademyVideoDeliveryStorage storage;
  private final Duration signedUrlTtl;

  public LmsContentDeliveryService(
      LmsContentRepository contentRepository,
      LmsLearnerAccessService accessService,
      AcademyVideoDeliveryStorage storage,
      AcademyVideoUploadProperties properties) {
    this.contentRepository = contentRepository;
    this.accessService = accessService;
    this.storage = storage;
    this.signedUrlTtl = Duration.ofMillis(properties.getDeliverySignedUrlTtlMs());
  }

  public ResponseEntity<StreamingResponseBody> get(
      String handle, Long contentId, String rangeHeader,
      JwtTokenProvider.AuthenticatedPrincipal principal) {
    Source source = resolveAuthorizedSource(handle, contentId, principal);
    try {
      ByteRange range = parseRange(rangeHeader, source.length());
      var target = storage.resolve(source.key(), VIDEO_MEDIA_TYPE, signedUrlTtl);
      if (target instanceof RedirectTarget redirect) {
        Duration redirectCache = signedUrlTtl.minusSeconds(60);
        if (redirectCache.isZero() || redirectCache.isNegative()) redirectCache = Duration.ofSeconds(1);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect.location())
            .cacheControl(CacheControl.maxAge(redirectCache).cachePrivate())
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .build();
      }
      LocalTarget local = (LocalTarget) target;
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

  public ResponseEntity<Void> head(
      String handle, Long contentId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    Source source = resolveAuthorizedSource(handle, contentId, principal);
    return ResponseEntity.ok()
        .cacheControl(PRIVATE_MEDIA)
        .contentType(MediaType.parseMediaType(VIDEO_MEDIA_TYPE))
        .contentLength(source.length())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .build();
  }

  private Source resolveAuthorizedSource(
      String handle, Long contentId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    LmsContent content = contentRepository.findById(contentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));
    LmsLearnerAccessService.Access access = accessService.requireAccess(
        handle, content.getLessonProduct().getLessonId(), principal);
    // 학생은 발행된 콘텐츠만 스트리밍할 수 있다. owner는 공개 전 미리보기가 필요해 상태와 무관하게 허용한다.
    if (!access.owner() && content.getStatus() != LmsContentStatus.PUBLISHED) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다.");
    }
    String key = content.getStorageKey();
    Long length = content.getSizeBytes();
    if (key == null || key.isBlank() || length == null || length < 1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상 소스를 찾을 수 없습니다.");
    }
    return new Source(key, length);
  }

  private ResponseEntity<StreamingResponseBody> stream(
      SeekableByteChannel channel, Source source, long start, long length,
      HttpStatus status, String contentRange) {
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
        .cacheControl(PRIVATE_MEDIA)
        .contentType(MediaType.parseMediaType(VIDEO_MEDIA_TYPE))
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

  private record Source(String key, long length) {}
  private record ByteRange(long start, long end) {
    long length() {
      return end - start + 1;
    }
  }
  private static final class UnsatisfiableRangeException extends RuntimeException {}
}
