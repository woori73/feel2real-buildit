package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * 애니메이션 WebP의 검증과 메타데이터 제거.
 *
 * <p><b>왜 재인코딩하지 않는가</b>: WebP를 다시 인코딩하려면 인코더가 필요한데, JDK에도 없고
 * 이 환경의 ffmpeg에도 {@code --enable-libwebp}가 없다(네이티브 webp 디코더는 ANIM/ANMF를
 * 처리하지 못한다). GIF로 변환하면 인코더 없이 되지만 같은 애니메이션이 <b>2.8배</b>로 불어난다
 * (측정값: 380KB → 1.06MB). WebP를 쓰는 이유가 용량인데 그걸 되돌리는 셈이라 채택하지 않았다.
 *
 * <p>대신 <b>픽셀 데이터는 원본 바이트 그대로 두고</b> 다음만 수행한다.
 * <ol>
 *   <li>구조 검증 — 청크 워크가 파일 끝에 정확히 맞아떨어지는지(잘린 파일 거부)</li>
 *   <li>용량 예산 — 바이트·프레임 수·캔버스 픽셀 상한</li>
 *   <li>프레임 수 일치 확인 — 프레임이 조용히 유실된 파일 거부</li>
 *   <li>메타데이터 제거 — EXIF/XMP/ICCP 청크 삭제(사진의 GPS 좌표가 공개 페이지로 새는 것 방지)</li>
 * </ol>
 *
 * <p>재인코딩이 아니므로 <b>화질·용량 손실이 없다</b>.
 *
 * <p><b>이 방식이 보장하지 못하는 것</b>(JPEG 재인코딩 경로와의 차이):
 * <ul>
 *   <li>픽셀 스트림에 심어진 페이로드를 떨궈내지 못한다. 재인코딩만이 그걸 한다.</li>
 *   <li>픽셀 내용의 무결성도 보장하지 않는다 — VP8은 오류 내성 코덱이라 페이로드가 수천 바이트
 *       손상돼도 예외 없이 "깨진 그림"으로 디코딩된다(실측).</li>
 *   <li>프레임이 실제로 그려지는지도 보장하지 않는다 — 픽셀을 디코딩하지 않는다. 우리 디코더가
 *       브라우저보다 약해 정상 파일을 거부하는 일이 있었기 때문이다
 *       ({@link #requireFrameCountMatches} 참고).</li>
 *   <li>최장변 1600px 축소가 걸리지 않는다. 그래서 {@link #MAX_BYTES}로 직접 묶는다.</li>
 * </ul>
 */
@Slf4j
public final class AnimatedWebp {

  /**
   * 프레임 수 상한.
   *
   * <p>실제 상한 역할은 {@link #MAX_BYTES}가 한다. 검증이 프레임 수에 비례하지 않기 때문이다
   * (픽셀을 디코딩하지 않는다 — {@link #requireFrameCountMatches} 참고). 469프레임 13MB 파일의 컨테이너
   * 스캔이 36ms였으므로 이 값은 넉넉히 잡는다. 30fps 기준 약 50초 분량이다.
   */
  static final int MAX_FRAMES = 1500;

  /** 캔버스 픽셀 상한. 디코딩된 프레임은 픽셀당 4바이트를 잡는다. */
  static final long MAX_CANVAS_PIXELS = 40_000_000L;

  /**
   * 바이트 상한. 재인코딩을 하지 않으니 축소(최장변 1600px)도 걸리지 않아, 저장물 크기를 여기서
   * 직접 묶어야 한다. {@code UploadSizeGuard}가 붙지 않은 업로드 경로(커뮤니티·블로그·학원 로고)도
   * 있으므로 상한을 이 클래스가 자체적으로 갖는다.
   */
  static final int MAX_BYTES = 20 * 1024 * 1024;

  /** VP8X 플래그 비트. 애니메이션 여부와, 제거 대상 메타데이터 청크의 존재를 알린다. */
  private static final int FLAG_ANIMATION = 0x02;
  private static final int FLAG_ICC = 0x20;
  private static final int FLAG_EXIF = 0x08;
  private static final int FLAG_XMP = 0x04;

  /** 픽셀이 아니라 부가정보만 담은 청크 — 통째로 버린다. */
  private static final String CHUNK_ICCP = "ICCP";
  private static final String CHUNK_EXIF = "EXIF";
  private static final String CHUNK_XMP = "XMP ";

  private static final int RIFF_HEADER_BYTES = 12;

  private AnimatedWebp() {
  }

  /** RIFF....WEBP 시그니처 여부. 픽셀은 건드리지 않는다. */
  public static boolean isWebp(byte[] bytes) {
    if (bytes == null || bytes.length < RIFF_HEADER_BYTES) return false;
    return ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP");
  }

  /**
   * VP8X의 ANIM 플래그로 애니메이션 여부를 판정한다. 픽셀을 디코딩하지 않는다.
   *
   * <p>정지 WebP면 {@code false} — 호출자는 기존 JPEG 경로를 그대로 태우면 된다.
   */
  public static boolean isAnimated(byte[] bytes) {
    if (!isWebp(bytes)) return false;
    int p = RIFF_HEADER_BYTES;
    while (p + 8 <= bytes.length) {
      String id = ascii(bytes, p, 4);
      int size = u32(bytes, p + 4);
      if (size < 0) return false;
      if (id.equals("VP8X")) {
        return p + 9 <= bytes.length && (bytes[p + 8] & FLAG_ANIMATION) != 0;
      }
      p += 8 + size + (size & 1);
    }
    return false;
  }

  /**
   * 애니메이션 WebP를 검증하고 메타데이터 청크를 제거한 바이트를 돌려준다.
   *
   * <p>픽셀 데이터(VP8/VP8L/ALPH)는 원본 그대로다 — 화질·용량 손실이 없다.
   *
   * @throws IOException 손상/잘린 파일, 프레임·픽셀 상한 초과, 디코딩 실패
   */
  public static byte[] validateAndStrip(byte[] originalBytes) throws IOException {
    if (!isWebp(originalBytes)) {
      throw new IOException("WebP 파일이 아닙니다.");
    }
    if (originalBytes.length > MAX_BYTES) {
      throw new IOException(
          "WebP 용량이 너무 큽니다: " + originalBytes.length + " bytes (최대 " + MAX_BYTES + ")");
    }
    Layout layout = walk(originalBytes);
    if (layout.frames > MAX_FRAMES) {
      throw new IOException(
          "WebP 프레임이 너무 많습니다: " + layout.frames + " (최대 " + MAX_FRAMES + ")");
    }
    if ((long) layout.canvasWidth * layout.canvasHeight > MAX_CANVAS_PIXELS) {
      throw new IOException(
          "WebP 화면이 너무 큽니다: " + layout.canvasWidth + "x" + layout.canvasHeight);
    }
    // 예산을 통과한 뒤에 스캔한다 — 순서가 바뀌면 폭탄 파일을 먼저 훑게 된다.
    requireFrameCountMatches(originalBytes, layout.frames);
    return stripMetadata(originalBytes);
  }

  /** 컨테이너를 훑어 캔버스 크기와 ANMF 수를 얻고, 동시에 구조를 검증한다. */
  private static Layout walk(byte[] bytes) throws IOException {
    int riffPayload = u32(bytes, 4);
    if (riffPayload < 4 || riffPayload > bytes.length - 8) {
      throw new IOException("WebP 파일이 손상되었거나 잘렸습니다.");
    }
    int end = 8 + riffPayload;
    int width = 0;
    int height = 0;
    int frames = 0;
    int p = RIFF_HEADER_BYTES;

    while (p + 8 <= end) {
      String id = ascii(bytes, p, 4);
      int size = u32(bytes, p + 4);
      if (size < 0 || p + 8 + size > end) {
        throw new IOException("WebP 파일이 손상되었거나 잘렸습니다.");
      }
      int body = p + 8;
      if (id.equals("VP8X")) {
        if (size < 10) throw new IOException("WebP 헤더가 손상되었습니다.");
        width = u24(bytes, body + 4) + 1;
        height = u24(bytes, body + 7) + 1;
      } else if (id.equals("ANMF")) {
        if (size < 16) throw new IOException("WebP 프레임 헤더가 손상되었습니다.");
        frames++;
      }
      p = body + size + (size & 1);
    }
    if (p != end) {
      throw new IOException("WebP 파일이 손상되었거나 잘렸습니다.");
    }
    if (frames < 1 || width < 1 || height < 1) {
      throw new IOException("WebP 애니메이션 프레임을 찾을 수 없습니다.");
    }
    return new Layout(width, height, frames);
  }

  /**
   * 컨테이너를 끝까지 스캔해 <b>디코더가 센 프레임 수가 컨테이너와 일치하는지</b> 확인한다.
   *
   * <p>프레임 데이터가 크게 깨지면 디코더는 예외를 던지지 않고 <b>읽히는 데까지만</b> 돌려준다.
   * 실측에서 ANMF 8개짜리 파일이 조용히 1프레임으로 줄었다. 그대로 저장하면 사용자는 8프레임을
   * 올렸는데 1프레임이 저장된다.
   *
   * <p><b>프레임 픽셀은 디코딩하지 않는다.</b> 두 방식을 시도했다가 모두 걷어냈다.
   * <ul>
   *   <li>전수 디코딩 — 469프레임(629x452) 파일에서 <b>15.6초 / 힙 301MB</b>. 업로드 요청에서
   *       감당 불가고, 큰 움짤 몇 개로 요청 스레드를 묶는 DoS가 된다.</li>
   *   <li>표본(첫·마지막) 디코딩 — 값어치가 없었다. 우리 디코더는 브라우저보다 약해서 <b>정상
   *       파일의 알파 프레임을 못 읽는다</b>. 실제 파일에서 {@code ALPH} 프레임 28개 중 10개가
   *       {@code ArrayIndexOutOfBoundsException: Coordinate out of bounds!}로 깨졌는데,
   *       컨테이너상 캔버스를 벗어나지 않는 정상 프레임이었고 브라우저는 정상 재생했다
   *       (TwelveMonkeys 3.11.0·3.12.0 동일). 이걸로 업로드를 막으면 멀쩡한 움짤이 거부된다.
   *       반대로 페이로드가 쓰레기여도 리더는 캔버스 크기 이미지를 그냥 돌려주므로 "디코딩되니까
   *       정상"이라는 보증도 못 준다.</li>
   * </ul>
   *
   * <p>재인코딩이 아니라 원본 바이트를 그대로 저장하므로, 확인용 디코더가 실제 렌더러보다 엄격할
   * 이유가 없다. 남는 값어치는 프레임 수 일치뿐이고 그건 이 스캔(실측 36ms)만으로 얻는다.
   */
  private static void requireFrameCountMatches(byte[] bytes, int expectedFrames) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (in == null) {
        throw new IOException("WebP 입력 스트림을 열 수 없습니다.");
      }
      ImageReader reader = webpReader();
      try {
        reader.setInput(in);

        int frames;
        try {
          frames = reader.getNumImages(true);
        } catch (IOException | RuntimeException e) {
          // 디코더는 EOFException처럼 메시지가 비어 있는 예외를 던진다. 그대로 올리면 사용자에게
          // 빈 에러가 나가므로 여기서 문구를 입힌다.
          throw new IOException("WebP를 디코딩할 수 없습니다.", e);
        }
        if (frames != expectedFrames) {
          throw new IOException(
              "WebP 프레임이 유실되었습니다: 컨테이너 " + expectedFrames + "개, 디코딩 " + frames + "개");
        }
      } finally {
        reader.dispose();
      }
    }
  }

  /**
   * 애니메이션을 읽을 수 있는 reader를 고른다.
   *
   * <p>WebP reader를 등록하는 라이브러리가 여럿 올라오면 ImageIO의 기본 순서가 애니메이션을 못 읽는
   * 쪽을 먼저 줄 수 있다. 프레임 1장만 돌려주면 나머지가 조용히 사라지므로 명시적으로 고른다.
   */
  private static ImageReader webpReader() throws IOException {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
    while (readers.hasNext()) {
      ImageReader reader = readers.next();
      if (reader.getClass().getName().startsWith("com.twelvemonkeys")) {
        return reader;
      }
      reader.dispose();
    }
    throw new IOException("WebP reader를 찾을 수 없습니다.");
  }

  /**
   * EXIF/XMP/ICCP 청크를 제거한 컨테이너를 새로 쓴다. 픽셀 청크는 바이트 그대로 복사한다.
   *
   * <p>VP8X의 해당 플래그 비트도 함께 내린다 — 청크는 없는데 플래그만 남으면 규격 위반이다.
   */
  private static byte[] stripMetadata(byte[] bytes) {
    int end = 8 + u32(bytes, 4);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    int p = RIFF_HEADER_BYTES;

    while (p + 8 <= end) {
      String id = ascii(bytes, p, 4);
      int size = u32(bytes, p + 4);
      int total = 8 + size + (size & 1);
      if (!id.equals(CHUNK_EXIF) && !id.equals(CHUNK_XMP) && !id.equals(CHUNK_ICCP)) {
        int from = body.size();
        body.write(bytes, p, Math.min(total, end - p));
        if (id.equals("VP8X")) {
          int flagsAt = from + 8;
          byte[] written = body.toByteArray();
          written[flagsAt] &= (byte) ~(FLAG_ICC | FLAG_EXIF | FLAG_XMP);
          body.reset();
          body.write(written, 0, written.length);
        }
      }
      p += total;
    }

    byte[] payload = body.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + RIFF_HEADER_BYTES);
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    writeU32(out, 4 + payload.length);
    out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(payload);
    return out.toByteArray();
  }

  private static void writeU32(ByteArrayOutputStream out, int value) {
    out.write(value & 0xFF);
    out.write((value >> 8) & 0xFF);
    out.write((value >> 16) & 0xFF);
    out.write((value >>> 24) & 0xFF);
  }

  private static String ascii(byte[] b, int offset, int length) {
    return new String(b, offset, length, StandardCharsets.US_ASCII);
  }

  private static int u24(byte[] b, int o) {
    return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16);
  }

  private static int u32(byte[] b, int o) {
    return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16)
        | ((b[o + 3] & 0xFF) << 24);
  }

  private record Layout(int canvasWidth, int canvasHeight, int frames) {
  }
}
