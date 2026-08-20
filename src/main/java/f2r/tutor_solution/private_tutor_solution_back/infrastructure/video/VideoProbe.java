package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

/**
 * ffprobe로 읽은 원본 영상 메타데이터. 업로드 제한(길이/코덱) 서버 재검증의 근거다.
 *
 * <p>클라이언트가 보낸 값은 신뢰하지 않고 항상 이 값으로 판정한다.
 */
public record VideoProbe(int width, int height, String videoCodec, double durationSec) {}
