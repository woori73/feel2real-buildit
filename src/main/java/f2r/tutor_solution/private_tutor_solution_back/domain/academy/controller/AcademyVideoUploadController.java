package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.CompleteRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.InitiateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVideoUploadDtos.SessionObservation;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVideoUploadSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/tutor/academy/videos/uploads")
@RequiredArgsConstructor
public class AcademyVideoUploadController {
  private final AcademyVideoUploadSessionService service;

  @PostMapping
  public ResponseEntity<InitiateResponse> initiate(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @Valid @RequestBody InitiateRequest request) {
    return ResponseEntity.ok(service.initiate(principal, request));
  }

  /** Local-development equivalent of a presigned S3 UploadPart capability. */
  @PutMapping(
      value = "/{sessionId}/parts/{partNumber}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<Void> uploadLocalPart(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String sessionId,
      @PathVariable int partNumber,
      @RequestParam String capability,
      HttpServletRequest request) throws IOException {
    String eTag = service.uploadLocalPart(
        principal, sessionId, partNumber, capability, request.getInputStream(),
        request.getContentLengthLong());
    return ResponseEntity.noContent().header(HttpHeaders.ETAG, eTag).build();
  }

  @PostMapping("/{sessionId}/complete")
  public ResponseEntity<SessionResponse> complete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String sessionId,
      @Valid @RequestBody CompleteRequest request) {
    var result = service.complete(principal, sessionId, request);
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
    if (result.response().retryAfterSeconds() != null) {
      builder.header(HttpHeaders.RETRY_AFTER,
          Integer.toString(result.response().retryAfterSeconds()));
    }
    return builder.body(result.response());
  }

  @DeleteMapping("/{sessionId}")
  public ResponseEntity<SessionResponse> abort(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String sessionId) {
    var result = service.abort(principal, sessionId);
    if (result.status().value() == 204) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.status(result.status()).body(result.response());
  }

  @GetMapping("/{sessionId}")
  public ResponseEntity<SessionObservation> observe(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable String sessionId) {
    return ResponseEntity.ok(service.observe(principal, sessionId));
  }
}
