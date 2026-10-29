package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.HomeAgentTrailerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public alias for one explicitly selected and currently approved file; Studio APIs stay private. */
@RestController
@RequiredArgsConstructor
public class HomeAgentTrailerController {
  private final HomeAgentTrailerService service;
  private final Semaphore streams = new Semaphore(3);

  @GetMapping(HomeAgentTrailerService.PUBLIC_PATH)
  public void stream(HttpServletRequest request, HttpServletResponse response,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) throws IOException {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (request.getQueryString() != null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 선택 인수는 지원하지 않습니다.");
    var media = service.approvedMedia().orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 트레일러를 준비 중입니다."));
    long start = 0;
    long end = media.size() - 1;
    if (range != null) {
      try {
        var ranges = HttpRange.parseRanges(range);
        if (ranges.size() != 1) throw new IllegalArgumentException();
        start = ranges.getFirst().getRangeStart(media.size());
        end = ranges.getFirst().getRangeEnd(media.size());
        if (start < 0 || start >= media.size() || end < start) throw new IllegalArgumentException();
      } catch (IllegalArgumentException invalid) {
        response.setStatus(416);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + media.size());
        return;
      }
    }
    if (!streams.tryAcquire())
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 재생해 주세요.");
    try (var input = service.openRange(media, start, end)) {
      long remaining = end - start + 1;
      response.setStatus(range == null ? 200 : 206);
      response.setContentType("video/mp4");
      response.setContentLengthLong(remaining);
      response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
      response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
      response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
      if (range != null) response.setHeader(HttpHeaders.CONTENT_RANGE,
          "bytes " + start + "-" + end + "/" + media.size());
      byte[] buffer = new byte[65536];
      var output = response.getOutputStream();
      while (remaining > 0) {
        int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (read < 0) throw new IOException("Incomplete media stream");
        output.write(buffer, 0, read);
        remaining -= read;
      }
    } catch (IOException unavailable) {
      // Never propagate an S3 key, filesystem path, credentials or the storage exception's cause.
      if (!response.isCommitted()) {
        response.reset();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.sendError(503, "트레일러를 불러올 수 없습니다.");
      } else {
        throw new IOException("Trailer streaming interrupted");
      }
    } finally {
      streams.release();
    }
  }
}
