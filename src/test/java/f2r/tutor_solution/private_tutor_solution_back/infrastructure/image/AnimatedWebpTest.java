package f2r.tutor_solution.private_tutor_solution_back.infrastructure.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnimatedWebpTest {

  /** 실제 애니메이션 WebP(8프레임 300x225). 손으로 만든 fixture로는 VP8 비트스트림을 못 만든다. */
  private static byte[] animatedWebp() throws IOException {
    try (InputStream in = AnimatedWebpTest.class.getResourceAsStream("/images/animated.webp")) {
      assertThat(in).as("테스트 fixture /images/animated.webp").isNotNull();
      return in.readAllBytes();
    }
  }

  private static byte[] staticWebp() throws IOException {
    // 애니메이션 fixture에서 ANIM/ANMF를 걷어내면 정지 WebP처럼 VP8X만 남는다.
    byte[] animated = animatedWebp();
    return stripChunks(animated, "ANIM", "ANMF");
  }

  @Test
  void isWebp_detectsRiffWebpSignatureOnly() throws IOException {
    // given: 실제 WebP와 GIF 시그니처
    byte[] webp = animatedWebp();
    byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    // when / then
    assertThat(AnimatedWebp.isWebp(webp)).isTrue();
    assertThat(AnimatedWebp.isWebp(gif)).isFalse();
    assertThat(AnimatedWebp.isWebp(new byte[]{1, 2, 3})).isFalse();
    assertThat(AnimatedWebp.isWebp(null)).isFalse();
  }

  @Test
  void isAnimated_trueForAnimatedWebp() throws IOException {
    // given: VP8X의 ANIM 플래그가 선 WebP
    byte[] webp = animatedWebp();

    // when / then: 픽셀을 디코딩하지 않고 헤더만으로 판정한다
    assertThat(AnimatedWebp.isAnimated(webp)).isTrue();
  }

  @Test
  void isAnimated_falseForNonAnimatedInput() throws IOException {
    // given: ANIM 청크를 걷어낸 WebP와 WebP가 아닌 바이트
    // when / then: 정지 이미지는 기존 JPEG 경로로 보내야 한다
    assertThat(AnimatedWebp.isAnimated(staticWebp())).isFalse();
    assertThat(AnimatedWebp.isAnimated("GIF89a".getBytes(StandardCharsets.US_ASCII))).isFalse();
  }

  @Test
  void validateAndStrip_keepsFramesAndPixelDataIntact() throws IOException {
    // given: 8프레임 애니메이션 WebP
    byte[] original = animatedWebp();

    // when
    byte[] result = AnimatedWebp.validateAndStrip(original);

    // then: 프레임 수와 해상도가 그대로다 (재인코딩이 아니라 검증 + 메타데이터 제거이므로)
    assertThat(frameCount(result)).isEqualTo(frameCount(original)).isEqualTo(8);
    assertThat(AnimatedWebp.isAnimated(result)).isTrue();
  }

  @Test
  void validateAndStrip_doesNotGrowTheFile() throws IOException {
    // given
    byte[] original = animatedWebp();

    // when
    byte[] result = AnimatedWebp.validateAndStrip(original);

    // then: 픽셀을 다시 인코딩하지 않으므로 절대 커지지 않는다 (WebP를 쓰는 이유가 용량이다)
    assertThat(result.length).isLessThanOrEqualTo(original.length);
  }

  @Test
  void validateAndStrip_removesExifChunk() throws IOException {
    // given: EXIF 청크(위치정보 등)를 끼워 넣은 애니메이션 WebP
    byte[] withExif = insertExifChunk(animatedWebp());
    assertThat(chunkIds(withExif)).contains("EXIF");

    // when
    byte[] result = AnimatedWebp.validateAndStrip(withExif);

    // then: 공개 페이지로 촬영 위치가 새지 않도록 제거된다
    assertThat(chunkIds(result)).doesNotContain("EXIF");
    assertThat(frameCount(result)).isEqualTo(8);
  }

  @Test
  void validateAndStrip_clearsExifFlagBitInVp8x() throws IOException {
    // given: EXIF 청크 + VP8X의 EXIF 플래그가 함께 선 파일
    byte[] withExif = insertExifChunk(animatedWebp());

    // when
    byte[] result = AnimatedWebp.validateAndStrip(withExif);

    // then: 청크만 지우고 플래그를 남기면 규격 위반이라 뷰어가 깨질 수 있다
    assertThat(vp8xFlags(result) & 0x08).isZero();
    assertThat(vp8xFlags(result) & 0x02).isEqualTo(0x02); // ANIM 플래그는 유지
  }

  @Test
  void validateAndStrip_rejectsTruncatedFile() throws IOException {
    // given: 뒤쪽이 잘려 나간 WebP
    byte[] original = animatedWebp();
    byte[] truncated = new byte[original.length / 2];
    System.arraycopy(original, 0, truncated, 0, truncated.length);

    // when / then: 조용히 프레임을 잃는 대신 업로드를 실패시킨다
    assertThatThrownBy(() -> AnimatedWebp.validateAndStrip(truncated))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("손상");
  }

  @Test
  void validateAndStrip_rejectsNonWebp() {
    // given: WebP가 아닌 바이트
    byte[] notWebp = "GIF89a-not-a-webp".getBytes(StandardCharsets.US_ASCII);

    // when / then
    assertThatThrownBy(() -> AnimatedWebp.validateAndStrip(notWebp))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("WebP 파일이 아닙니다");
  }

  @Test
  void validateAndStrip_acceptsFileWhenSomeFramesFailToDecode() throws IOException {
    // given: 첫 프레임은 정상이고 마지막 프레임만 우리 디코더가 못 읽는 파일.
    // 실제 사례 — 알파(ALPH) 프레임 28개 중 10개가 TwelveMonkeys에서
    // ArrayIndexOutOfBoundsException으로 깨졌지만 브라우저는 정상 재생했다(3.11.0·3.12.0 동일).
    byte[] tampered = appendUndecodableAnmf(animatedWebp());

    // when
    byte[] result = AnimatedWebp.validateAndStrip(tampered);

    // then: 확인용 디코더가 렌더러보다 엄격하면 잘 재생되는 파일을 우리만 거부하게 된다.
    // 재인코딩이 아니라 원본을 그대로 저장하므로 개별 프레임 실패로 업로드를 막지 않는다.
    assertThat(result).isNotEmpty();
    assertThat(AnimatedWebp.isAnimated(result)).isTrue();
  }

  @Test
  void validateAndStrip_acceptsFileWhoseFramesOurDecoderCannotRender() throws IOException {
    // given: 컨테이너 구조는 유효하지만 픽셀 subchunk가 없어 우리 디코더가 그릴 수 없는 프레임들
    byte[] undecodable = animatedWebpWithUndecodableFrames();

    // when
    byte[] result = AnimatedWebp.validateAndStrip(undecodable);

    // then: 픽셀 디코딩으로 판정하지 않는다는 정책을 고정한다.
    // 우리 디코더는 브라우저보다 약해 정상 파일도 못 읽으므로, 렌더 가능 여부로 업로드를 막지 않는다.
    assertThat(result).isNotEmpty();
    assertThat(AnimatedWebp.isAnimated(result)).isTrue();
  }

  @Test
  void validateAndStrip_rejectsHeavilyCorruptedFile() throws IOException {
    // given: 여러 청크 헤더를 가로질러 크게 손상된 파일
    byte[] corrupt = animatedWebp();
    int firstAnmf = indexOf(corrupt, "ANMF");
    for (int i = firstAnmf + 24; i < Math.min(corrupt.length, firstAnmf + 24 + 20000); i++) {
      corrupt[i] = (byte) 0xFF;
    }

    // when / then: 청크 워크가 파일 끝에 맞아떨어지지 않아 구조 검증에서 걸린다
    assertThatThrownBy(() -> AnimatedWebp.validateAndStrip(corrupt))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("손상");
  }

  @Test
  void validateAndStrip_acceptsMildPixelCorruption() throws IOException {
    // given: 페이로드가 조금(100바이트) 손상됐지만 프레임 수는 유지되는 파일
    byte[] corrupt = animatedWebp();
    int firstAnmf = indexOf(corrupt, "ANMF");
    for (int i = firstAnmf + 24; i < Math.min(corrupt.length, firstAnmf + 24 + 100); i++) {
      corrupt[i] = (byte) 0xFF;
    }

    // when / then: VP8은 오류 내성 코덱이라 이건 통과한다 — 재인코딩이 아니므로 픽셀 무결성은
    // 보장 범위 밖이라는 사실을 명시적으로 고정해 둔다(회귀 시 이 테스트가 깨진다).
    byte[] result = AnimatedWebp.validateAndStrip(corrupt);
    assertThat(frameCount(result)).isEqualTo(8);
  }

  @Test
  void validateAndStrip_rejectsOversizedInput() throws IOException {
    // given: 바이트 상한을 넘긴 입력 (앞부분은 정상 WebP 시그니처)
    byte[] original = animatedWebp();
    byte[] huge = new byte[AnimatedWebp.MAX_BYTES + 1];
    System.arraycopy(original, 0, huge, 0, original.length);

    // when / then: 재인코딩/축소가 없으므로 여기서 막지 않으면 원본이 그대로 저장된다
    assertThatThrownBy(() -> AnimatedWebp.validateAndStrip(huge))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("용량이 너무 큽니다");
  }

  // ---------- helpers ----------

  private static int frameCount(byte[] webp) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(webp))) {
      ImageReader reader = twelveMonkeys();
      try {
        reader.setInput(in);
        return reader.getNumImages(true);
      } finally {
        reader.dispose();
      }
    }
  }

  private static ImageReader twelveMonkeys() {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
    while (readers.hasNext()) {
      ImageReader reader = readers.next();
      if (reader.getClass().getName().startsWith("com.twelvemonkeys")) return reader;
    }
    throw new IllegalStateException("TwelveMonkeys WebP reader not found");
  }

  private static java.util.List<String> chunkIds(byte[] b) {
    java.util.List<String> ids = new java.util.ArrayList<>();
    int end = 8 + u32(b, 4);
    int p = 12;
    while (p + 8 <= end) {
      ids.add(new String(b, p, 4, StandardCharsets.US_ASCII));
      int size = u32(b, p + 4);
      p += 8 + size + (size & 1);
    }
    return ids;
  }

  private static int vp8xFlags(byte[] b) {
    int p = 12;
    while (p + 8 <= b.length) {
      if (new String(b, p, 4, StandardCharsets.US_ASCII).equals("VP8X")) return b[p + 8] & 0xFF;
      int size = u32(b, p + 4);
      p += 8 + size + (size & 1);
    }
    throw new IllegalStateException("VP8X not found");
  }

  /** EXIF 청크를 파일 끝에 붙이고 VP8X의 EXIF 플래그를 세운다. */
  private static byte[] insertExifChunk(byte[] b) {
    byte[] payload = "Exif\0\0FAKE-GPS-DATA".getBytes(StandardCharsets.US_ASCII);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(b, 12, 8 + u32(b, 4) - 12);
    body.writeBytes("EXIF".getBytes(StandardCharsets.US_ASCII));
    body.write(payload.length & 0xFF);
    body.write((payload.length >> 8) & 0xFF);
    body.write((payload.length >> 16) & 0xFF);
    body.write((payload.length >>> 24) & 0xFF);
    body.writeBytes(payload);
    if ((payload.length & 1) == 1) body.write(0);

    byte[] inner = body.toByteArray();
    // VP8X는 payload 맨 앞 청크다 — flags 바이트는 그 8바이트 헤더 뒤.
    inner[8] |= 0x08;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    int size = 4 + inner.length;
    out.write(size & 0xFF);
    out.write((size >> 8) & 0xFF);
    out.write((size >> 16) & 0xFF);
    out.write((size >>> 24) & 0xFF);
    out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(inner);
    return out.toByteArray();
  }

  /**
   * 모든 ANMF를 "헤더는 유효하지만 픽셀 subchunk가 없는" 프레임으로 바꾼다.
   *
   * <p>페이로드를 쓰레기로 덮는 것으로는 부족하다 — VP8은 오류 내성 코덱이라 예외 없이 깨진 그림으로
   * 디코딩된다(별도 테스트로 고정). 디코딩 자체가 불가능하려면 픽셀 데이터가 아예 없어야 한다.
   */
  private static byte[] animatedWebpWithUndecodableFrames() throws IOException {
    byte[] b = animatedWebp();
    int end = 8 + u32(b, 4);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    int p = 12;
    while (p + 8 <= end) {
      String id = new String(b, p, 4, StandardCharsets.US_ASCII);
      int size = u32(b, p + 4);
      int total = 8 + size + (size & 1);
      if (id.equals("ANMF")) {
        byte[] header = new byte[16];
        System.arraycopy(b, p + 8, header, 0, 16);
        byte[] payload = new byte[header.length + 4];
        System.arraycopy(header, 0, payload, 0, 16);
        System.arraycopy(new byte[]{0x11, 0x22, 0x33, 0x44}, 0, payload, 16, 4);
        body.writeBytes("ANMF".getBytes(StandardCharsets.US_ASCII));
        writeU32(body, payload.length);
        body.writeBytes(payload);
      } else {
        body.write(b, p, Math.min(total, end - p));
      }
      p += total;
    }
    return riffWrap(body.toByteArray());
  }

  private static void writeU32(ByteArrayOutputStream out, int value) {
    out.write(value & 0xFF);
    out.write((value >> 8) & 0xFF);
    out.write((value >> 16) & 0xFF);
    out.write((value >>> 24) & 0xFF);
  }

  private static byte[] riffWrap(byte[] payload) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    writeU32(out, 4 + payload.length);
    out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(payload);
    return out.toByteArray();
  }

  /** 헤더는 유효하지만 내용은 디코딩할 수 없는 ANMF를 하나 덧붙인다. */
  private static byte[] appendUndecodableAnmf(byte[] b) {
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    for (int i = 0; i < 6; i++) frame.write(0);      // x/2, y/2 (3+3 bytes)
    frame.write(9); frame.write(0); frame.write(0);  // width-1
    frame.write(9); frame.write(0); frame.write(0);  // height-1
    frame.write(10); frame.write(0); frame.write(0); // duration
    frame.write(0x02);                                // blend/disposal
    frame.writeBytes(new byte[]{0x11, 0x22, 0x33, 0x44}); // 유효한 VP8/VP8L이 아닌 페이로드
    byte[] payload = frame.toByteArray();

    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(b, 12, 8 + u32(b, 4) - 12);
    body.writeBytes("ANMF".getBytes(StandardCharsets.US_ASCII));
    body.write(payload.length & 0xFF);
    body.write((payload.length >> 8) & 0xFF);
    body.write((payload.length >> 16) & 0xFF);
    body.write((payload.length >>> 24) & 0xFF);
    body.writeBytes(payload);
    if ((payload.length & 1) == 1) body.write(0);

    byte[] inner = body.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    int size = 4 + inner.length;
    out.write(size & 0xFF);
    out.write((size >> 8) & 0xFF);
    out.write((size >> 16) & 0xFF);
    out.write((size >>> 24) & 0xFF);
    out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(inner);
    return out.toByteArray();
  }

  private static byte[] stripChunks(byte[] b, String... drop) {
    java.util.Set<String> dropSet = java.util.Set.of(drop);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    int end = 8 + u32(b, 4);
    int p = 12;
    while (p + 8 <= end) {
      String id = new String(b, p, 4, StandardCharsets.US_ASCII);
      int size = u32(b, p + 4);
      int total = 8 + size + (size & 1);
      if (!dropSet.contains(id)) body.write(b, p, Math.min(total, end - p));
      p += total;
    }
    byte[] inner = body.toByteArray();
    inner[8] &= (byte) ~0x02; // ANIM 플래그 해제
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    int size = 4 + inner.length;
    out.write(size & 0xFF);
    out.write((size >> 8) & 0xFF);
    out.write((size >> 16) & 0xFF);
    out.write((size >>> 24) & 0xFF);
    out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(inner);
    return out.toByteArray();
  }

  private static int indexOf(byte[] b, String chunkId) {
    byte[] needle = chunkId.getBytes(StandardCharsets.US_ASCII);
    outer:
    for (int i = 12; i + needle.length <= b.length; i++) {
      for (int j = 0; j < needle.length; j++) if (b[i + j] != needle[j]) continue outer;
      return i;
    }
    throw new IllegalStateException(chunkId + " not found");
  }

  private static int u32(byte[] b, int o) {
    return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16)
        | ((b[o + 3] & 0xFF) << 24);
  }
}
