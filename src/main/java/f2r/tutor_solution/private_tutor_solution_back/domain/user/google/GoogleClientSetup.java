package f2r.tutor_solution.private_tutor_solution_back.domain.user.google;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** First-use credential file owned by the app user, separate from all public content. */
public final class GoogleClientSetup {
    static final Path FILE = Path.of("/var/lib/f2r-google/web-client.json");
    static final int LIMIT = 16384;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CALLBACK = "https://feel2real.co.kr/login/oauth2/code/";
    private GoogleClientSetup() {}

    static Map<String, Object> settings(byte[] bytes) {
        try {
            if (bytes.length > LIMIT) throw new IllegalArgumentException();
            var web = JSON.readTree(bytes).path("web");
            String id = web.path("client_id").asText("");
            String secret = web.path("client_secret").asText("");
            Set<String> uris = new HashSet<>();
            web.path("redirect_uris").forEach(uri -> uris.add(uri.asText()));
            if (id.matches("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com")
                    && secret.matches("[A-Za-z0-9_-]{8,256}")
                    && uris.containsAll(Set.of(CALLBACK + "google", CALLBACK + "google-student"))) {
                return Map.of("GOOGLE_CLIENT_ID", id, "GOOGLE_CLIENT_SECRET", secret);
            }
        } catch (RuntimeException ignored) {
            // The parser exception can contain fragments of the secret document.
        }
        throw new IllegalArgumentException("Google 웹 클라이언트 JSON과 F2R 복귀 주소 두 개를 확인해 주세요.");
    }

    static void register(Path file, InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(LIMIT + 1);
        settings(bytes);
        Path tmp = Files.createTempFile(file.getParent(), ".upload-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.write(tmp, bytes);
            // Atomically publish the completed file without overwriting a concurrent first upload.
            Files.createLink(file, tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static void applyClient(ConfigurableEnvironment environment, Map<String, Object> client) {
        // Both registrations must use the same installed ID/secret pair, even when a
        // previous deployment supplied credentials through systemd environment variables.
        String prefix = "spring.security.oauth2.client.registration.";
        var registrations = new HashMap<String, Object>();
        for (String name : Set.of("google", "google-student")) {
            registrations.put(prefix + name + ".client-id", client.get("GOOGLE_CLIENT_ID"));
            registrations.put(prefix + name + ".client-secret", client.get("GOOGLE_CLIENT_SECRET"));
        }
        environment.getPropertySources().addFirst(new MapPropertySource("f2rGoogleWebClient", registrations));
    }

    public static void configureProduction(SpringApplication app) {
        String profiles = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
        if (Arrays.stream(profiles.split(",")).map(String::trim).noneMatch("prd"::equals)
                || !Files.isRegularFile(FILE, LinkOption.NOFOLLOW_LINKS)) return;
        try (var input = Files.newInputStream(FILE)) {
            var client = settings(input.readNBytes(LIMIT + 1));
            app.setDefaultProperties(client);
            app.addInitializers(context -> applyClient(context.getEnvironment(), client));
            app.setAdditionalProfiles("google-accounts");
        } catch (IOException | IllegalArgumentException failure) {
            LoggerFactory.getLogger(GoogleClientSetup.class).error("Google client configuration could not be loaded");
        }
    }
}
