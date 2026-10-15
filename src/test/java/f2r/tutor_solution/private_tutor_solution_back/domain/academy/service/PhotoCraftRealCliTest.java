package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.PhotoCraftImageService.EditResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.PhotoCraftImageService.ImageOperation;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실제 PhotoCraft CLI 호출 검증 (PHOTOCRAFT_CLI 환경변수가 있을 때만 실행).
 * 운영/AWS와 무관하게 임시 디렉터리에서만 동작한다.
 */
@EnabledIfEnvironmentVariable(named = "PHOTOCRAFT_CLI", matches = ".+")
class PhotoCraftRealCliTest {

  @TempDir Path dir;
  PhotoCraftImageService service;
  Path original;

  @BeforeEach
  void setUp() throws Exception {
    service = new PhotoCraftImageService(new ObjectMapper());
    ReflectionTestUtils.setField(service, "cliPath", System.getenv("PHOTOCRAFT_CLI"));
    ReflectionTestUtils.setField(service, "timeoutSec", 30);
    original = dir.resolve("original.jpg");
    BufferedImage img = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 200; x++) for (int y = 0; y < 120; y++) img.setRGB(x, y, 0x707070);
    ImageIO.write(img, "jpg", original.toFile());
  }

  @Test
  void brightnessEdit_brightensImage_andKeepsOriginalUntouched() throws Exception {
    // given
    String before = sha(original);
    Path out = dir.resolve("out/edited.png");
    // when
    EditResult r = service.editImage(original.toString(), out.toString(),
        List.of(ImageOperation.brightness(30)));
    // then
    assertThat(r.success()).isTrue();
    assertThat(Files.exists(out)).isTrue();
    assertThat(mean(out)).isGreaterThan(mean(original) + 5);
    assertThat(sha(original)).isEqualTo(before);
  }

  @Test
  void exposureAndVibranceEdit_succeedsWithRealCli() {
    // given
    Path out = dir.resolve("out/multi.png");
    // when
    EditResult r = service.editImage(original.toString(), out.toString(),
        List.of(ImageOperation.exposure(1.0), ImageOperation.vibrance(20)));
    // then
    assertThat(r.success()).isTrue();
    assertThat(Files.exists(out)).isTrue();
  }

  @Test
  void corruptInput_failsAndLeavesOriginalAndNoOutput() throws Exception {
    // given
    Path bad = dir.resolve("bad.jpg");
    Files.writeString(bad, "not an image");
    String before = sha(bad);
    Path out = dir.resolve("out/never.png");
    // when
    EditResult r = service.editImage(bad.toString(), out.toString(),
        List.of(ImageOperation.brightness(30)));
    // then
    assertThat(r.success()).isFalse();
    assertThat(Files.exists(out)).isFalse();
    assertThat(sha(bad)).isEqualTo(before);
  }

  private static String sha(Path p) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
  }

  private static double mean(Path p) throws Exception {
    BufferedImage i = ImageIO.read(p.toFile());
    long sum = 0;
    for (int x = 0; x < i.getWidth(); x++) for (int y = 0; y < i.getHeight(); y++) sum += i.getRGB(x, y) & 0xFF;
    return sum / (double) (i.getWidth() * i.getHeight());
  }
}
