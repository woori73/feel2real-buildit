package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import java.nio.file.Path;

/**
 * 변환 산출물. 파일 경로로 넘기고 byte[]로 올리지 않는다 — 100MB급 입력을 힙에 상주시키지 않기 위해서다.
 *
 * <p>두 경로 모두 호출자가 사용 후 삭제할 책임을 진다.
 */
public record TranscodeResult(
    Path mp4, Path thumbnail, int width, int height, int durationSec, long sizeBytes) {}
