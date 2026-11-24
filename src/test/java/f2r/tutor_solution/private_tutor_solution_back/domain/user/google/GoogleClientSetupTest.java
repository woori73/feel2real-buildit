package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoogleClientSetupTest {
    private static final String SAMPLE = """
        {"web":{"client_id":"demo.apps.googleusercontent.com","client_secret":"synthetic-only",
        "redirect_uris":["https://feel2real.co.kr/login/oauth2/code/google",
                         "https://feel2real.co.kr/login/oauth2/code/google-student"]}}
        """;
    static ByteArrayInputStream input(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
    @Test void installsPrivatelyAndOnlyOnce(@TempDir Path dir) throws Exception {
        Path dest = dir.resolve("web-client.json");
        GoogleClientSetup.register(dest, input(SAMPLE));
        assertThat(Files.getPosixFilePermissions(dest)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThatThrownBy(() -> GoogleClientSetup.register(dest, input(SAMPLE.replace("synthetic-only","other-secret"))))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(dest)).isEqualTo(SAMPLE);
        assertThat(GoogleClientSetup.settings(Files.readAllBytes(dest))).containsEntry("GOOGLE_CLIENT_SECRET","synthetic-only");
    }

    @Test void rejectsMissingCallbackAndOversizeFileWithoutPersisting(@TempDir Path dir) {
        Path dest = dir.resolve("web-client.json");
        for (String bad : new String[]{SAMPLE.replace("google-student","other"), SAMPLE + " ".repeat(16384)}) {
            assertThatThrownBy(() -> GoogleClientSetup.register(dest, input(bad))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(dest).doesNotExist();
    }
}
