package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyFinancialCollectorDtos.Snapshot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Single-node local prototype vault; ciphertext only, immutable objects, no credential API. */
@Service
@Profile("local & !prd")
@ConditionalOnProperty(name = "app.easy.financial-collector.enabled", havingValue = "true")
public class EasyFinancialVault {
  private final Path root;
  private final SecretKeySpec key;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final SecureRandom random = new SecureRandom();

  public EasyFinancialVault(@Value("${app.easy.financial-collector.vault-dir}") String directory,
      @Value("${app.easy.financial-collector.key-base64}") String keyBase64) {
    byte[] decoded;
    try { decoded = Base64.getDecoder().decode(keyBase64); }
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("VAULT_KEY_INVALID"); }
    if (decoded.length != 32) throw new IllegalArgumentException("VAULT_KEY_MUST_BE_256_BITS");
    key = new SecretKeySpec(decoded, "AES");
    root = Path.of(directory).toAbsolutePath().normalize();
    // Keep financial objects out of the repository and public static file roots.
    if (!Path.of(directory).isAbsolute() || root.startsWith(Path.of("").toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("VAULT_MUST_BE_ABSOLUTE_AND_OUTSIDE_WORKTREE");
    }
    try {
      Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      if (!root.toRealPath().equals(root)) throw new IllegalArgumentException("VAULT_SYMLINK_REJECTED");
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
    } catch (IOException e) { throw new IllegalStateException("VAULT_UNAVAILABLE"); }
  }

  public void save(Snapshot snapshot) {
    write(snapshot.businessId(), snapshot.collectionId(), "original", snapshot);
  }

  public Snapshot read(Long businessId, String id) {
    Path path = objectPath(businessId, id, "original");
    try {
      if (Files.isSymbolicLink(path) || Files.size(path) > 64L * 1024 * 1024) {
        throw new IllegalStateException("VAULT_OBJECT_INVALID");
      }
      byte[] envelope = Files.readAllBytes(path);
      if (envelope.length < 30 || envelope[0] != 1) throw new IllegalStateException("VAULT_VERSION_INVALID");
      ByteBuffer in = ByteBuffer.wrap(envelope);
      in.get();
      byte[] nonce = new byte[12];
      in.get(nonce);
      byte[] encrypted = new byte[in.remaining()];
      in.get(encrypted);
      Cipher cipher = cipher(Cipher.DECRYPT_MODE, nonce, businessId, id, "original");
      Snapshot snapshot = mapper.readValue(cipher.doFinal(encrypted), Snapshot.class);
      if (!businessId.equals(snapshot.businessId()) || !id.equals(snapshot.collectionId())) {
        throw new IllegalStateException("VAULT_SCOPE_INVALID");
      }
      return snapshot;
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("VAULT_OBJECT_UNAVAILABLE_OR_TAMPERED");
    }
  }

  public void audit(Long businessId, String id, String action, String actor, Long runId) {
    // One immutable authenticated object per event. No raw row, filename, session or exception text.
    write(businessId, id, "audit-" + UUID.randomUUID(), Map.of("action", action,
        "actor", actor, "at", Instant.now().toString(), "runId", runId == null ? 0L : runId));
  }

  private void write(Long businessId, String id, String object, Object value) {
    Path destination = objectPath(businessId, id, object);
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, nonce, businessId, id, object)
          .doFinal(mapper.writeValueAsBytes(value));
      byte[] envelope = ByteBuffer.allocate(13 + encrypted.length).put((byte) 1).put(nonce).put(encrypted).array();
      if (envelope.length > 64 * 1024 * 1024) throw new IllegalStateException("VAULT_OBJECT_TOO_LARGE");
      // Create with private permissions from the outset; never write plaintext temporary files.
      try (var channel = Files.newByteChannel(destination,
          Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        while (buffer.hasRemaining()) channel.write(buffer);
      }
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("VAULT_WRITE_FAILED");
    }
  }

  private Path objectPath(Long businessId, String id, String object) {
    if (businessId == null || businessId < 1 || !UUID.fromString(id).toString().equals(id)
        || !object.matches("original|audit-[a-f0-9-]{36}")) throw new IllegalArgumentException("VAULT_ID_INVALID");
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("VAULT_ROOT_INVALID");
    }
    return root.resolve(businessId + "-" + id + "-" + object + ".enc");
  }

  private Cipher cipher(int mode, byte[] nonce, Long businessId, String id, String object)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, key, new GCMParameterSpec(128, nonce));
    cipher.updateAAD(("v1:" + businessId + ":" + id + ":" + object).getBytes(StandardCharsets.UTF_8));
    return cipher;
  }
}
