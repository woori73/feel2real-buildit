package f2r.tutor_solution.private_tutor_solution_back.config;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VideoUploadRejectedException;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.exception.RejectedFilesException;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.exception.SourceProcessingNotCompleteException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoToolUnavailableException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscodeException;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.VideoTranscoderBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** 비활성화(anthropic.enabled=false)된 Claude 연동 기능 호출 — 503 + 안내. */
    @ExceptionHandler(DemoFeatureDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDemoFeatureDisabled(DemoFeatureDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", e.getMessage(),
                "demo", true));
    }

    /**
     * ffmpeg/ffprobe 바이너리 부재 — 503 + 안내.
     * 배포 환경에 아직 바이너리가 없어도 앱은 떠 있어야 하므로 부팅이 아니라 호출 시점에 막는다.
     */
    @ExceptionHandler(VideoToolUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleVideoToolUnavailable(VideoToolUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "영상 변환 기능을 사용할 수 없습니다. 관리자에게 문의해 주세요."));
    }

    /** 동시 변환 상한 초과 — 대기시키지 않고 즉시 재시도를 안내한다. */
    @ExceptionHandler(VideoTranscoderBusyException.class)
    public ResponseEntity<Map<String, Object>> handleVideoTranscoderBusy(VideoTranscoderBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", e.getMessage()));
    }

    /** 영상 업로드 제한(길이·용량·개수·코덱) 위반 — 어느 제한에 걸렸는지 그대로 화면에 보여준다. */
    @ExceptionHandler(VideoUploadRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleVideoUploadRejected(VideoUploadRejectedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", e.getMessage()));
    }

    /** 변환 실패(비정상 exit/타임아웃/손상 입력) — 사용자가 올린 파일 문제일 수 있어 400으로 안내한다. */
    @ExceptionHandler(VideoTranscodeException.class)
    public ResponseEntity<Map<String, Object>> handleVideoTranscode(VideoTranscodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", e.getMessage()));
    }

    /**
     * 컨테이너 multipart 하드컷. 업무 상한(영상 200MiB / 이미지·PDF 20MB)은 각 서비스가 판정하므로
     * 여기까지 오는 것은 그보다 큰 요청뿐이다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        // 여기까지 왔다는 건 앱 계층 판정 전에 컨테이너가 끊었다는 뜻이라 어느 업무 상한인지 알 수 없다.
        // 사용자가 다음에 뭘 해야 할지는 알려줘야 하므로 두 상한을 함께 안내한다.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "파일이 너무 큽니다. 영상은 200MiB 이하, 이미지·PDF는 20MB 이하로 올려 주세요."));
    }

    /** AuthenticatedUserResolver의 IDOR 차단(403) — 다른 학생/사용자 자원 접근 시도. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(RejectedFilesException.class)
    public ResponseEntity<Map<String, Object>> handleRejectedFiles(RejectedFilesException e) {
        List<Map<String, String>> rejected = e.getRejectedFiles().stream()
                .map(f -> Map.of("fileName", f.fileName() != null ? f.fileName() : "",
                        "reason", f.reason()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "업로드 파일 중 일부가 거부되었습니다.",
                "rejectedFiles", rejected));
    }

    @ExceptionHandler(SourceProcessingNotCompleteException.class)
    public ResponseEntity<Map<String, Object>> handleSourceProcessingNotComplete(
            SourceProcessingNotCompleteException e) {
        List<Map<String, Object>> pending = e.getPendingSources().stream()
                .map(s -> Map.<String, Object>of(
                        "sourceId", s.sourceId(),
                        "fileName", s.fileName() != null ? s.fileName() : "",
                        "status", s.status().name()))
                .toList();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", e.getMessage(),
                "pendingSources", pending));
    }
}
