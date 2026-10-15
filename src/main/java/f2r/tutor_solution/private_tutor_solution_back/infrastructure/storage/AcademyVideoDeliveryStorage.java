package f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.time.Duration;

/** Storage-specific public delivery without whole-object JVM buffering. */
public interface AcademyVideoDeliveryStorage {

  DeliveryTarget resolve(String key, String mediaType, Duration signedUrlTtl) throws IOException;

  sealed interface DeliveryTarget permits LocalTarget, RedirectTarget {}

  record LocalTarget(SeekableByteChannel channel, long length) implements DeliveryTarget {}

  record RedirectTarget(URI location) implements DeliveryTarget {}
}
