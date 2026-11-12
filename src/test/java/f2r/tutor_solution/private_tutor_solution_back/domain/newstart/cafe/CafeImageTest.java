package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CafeImageTest {
  static CafeImage image(int color) throws IOException {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, color);
    var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
    return new CafeImage("image/png", out.toByteArray());
  }

  @Test
  void givenValidPng_whenPrepared_thenDefensiveCopiesAndImageContentAffectsDuplicateHash() throws Exception {
    var a = image(0);
    var b = image(0xffffff);
    byte[] exposed = a.bytes(); exposed[0] = 0;
    assertEquals((byte) 0x89, a.bytes()[0]);
    var plain = new CafeService.Post("cases", "제목", "본문", "image-request-1");
    var first = new CafeService.Post("cases", "제목", "본문", "image-request-1", List.of(a, b));
    var swapped = new CafeService.Post("cases", "제목", "본문", "image-request-1", List.of(b, a));
    assertNotEquals(CafeService.prepare(plain).payloadHash(), CafeService.prepare(first).payloadHash());
    assertNotEquals(CafeService.prepare(first).payloadHash(), CafeService.prepare(swapped).payloadHash());
    assertEquals(CafeService.prepare(first).payloadHash(), CafeService.prepare(
        new CafeService.Post("cases", "제목", "본문", "different-request", List.of(image(0), image(0xffffff)))).payloadHash());
  }

  @Test
  void givenSpoofedOrOversizedImage_whenAccepted_thenRejectedBeforePublication() throws Exception {
    assertThrows(ResponseStatusException.class, () -> new CafeImage("image/png", "<svg/>".getBytes()));
    assertThrows(ResponseStatusException.class, () -> new CafeImage("image/jpeg", image(0).bytes()));
    assertThrows(ResponseStatusException.class, () -> new CafeImage("image/png", new byte[CafeImage.MAX_BYTES + 1]));
    assertThrows(ResponseStatusException.class, () -> CafeImage.validate(Collections.nCopies(6, image(0))));
    byte[] padded = Arrays.copyOf(image(0).bytes(), 3 * 1024 * 1024 + 1);
    var large = new CafeImage("image/png", padded);
    assertThrows(ResponseStatusException.class, () -> CafeImage.validate(Collections.nCopies(4, large)));
  }
}
