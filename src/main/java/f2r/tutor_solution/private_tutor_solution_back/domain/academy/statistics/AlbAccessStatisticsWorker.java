package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.config.AlbAccessStatisticsProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.statistics.alb.enabled", havingValue = "true")
public class AlbAccessStatisticsWorker {
  private final AlbAccessStatisticsProperties properties;
  private final PublicSiteProperties publicSiteProperties;
  private final AcademyHandleHistoryRepository handleHistoryRepository;
  private final AcademyAccessStatisticsStore store;
  private final AcademyAccessStatisticsImportService importService;
  private S3Client s3;

  @PostConstruct
  void init() {
    if (properties.getBucket() == null || properties.getBucket().isBlank()) {
      throw new IllegalStateException("ALB access log bucket is required when statistics import is enabled");
    }
    if (properties.getKeyPrefix() == null
        || properties.getKeyPrefix().isBlank()
        || !properties.getKeyPrefix().contains("AWSLogs/")) {
      throw new IllegalStateException(
          "ALB access log prefix must include the isolated AWSLogs/ path");
    }
    if (publicSiteProperties.baseDomain() == null || publicSiteProperties.baseDomain().isBlank()) {
      throw new IllegalStateException("Public base domain is required when ALB statistics import is enabled");
    }
    s3 = S3Client.builder().region(Region.of(properties.getRegion())).build();
  }

  @PreDestroy
  void close() {
    if (s3 != null) s3.close();
  }

  @Scheduled(
      fixedDelayString = "#{@albAccessStatisticsProperties.pollDelayMs}",
      scheduler = "albAccessStatisticsScheduler")
  public void poll() {
    int importedObjects = 0;
    String continuationToken = null;
    do {
      ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
          .bucket(properties.getBucket())
          .prefix(properties.getKeyPrefix())
          .continuationToken(continuationToken)
          .build());
      for (S3Object object : page.contents()) {
        if (importedObjects >= properties.getMaxObjectsPerPoll()) return;
        if (!object.key().endsWith(".log.gz") || store.wasImported(object.key())) continue;
        importOne(object);
        importedObjects++;
      }
      continuationToken = page.nextContinuationToken();
    } while (continuationToken != null);
  }

  private void importOne(S3Object object) {
    AcademyHandleResolver resolver = new AcademyHandleResolver(handleHistoryRepository
        .findAllByOrderByHandleAscValidFromUtcAsc().stream()
        .map(history -> new AcademyHandleResolver.HandlePeriod(
            history.getAcademy().getAcademyId(), history.getHandle(),
            history.getValidFromUtc(), history.getValidToUtc()))
        .toList());
    AlbAccessLogParser parser = new AlbAccessLogParser(publicSiteProperties.baseDomain());
    ZoneId zoneId = ZoneId.of(properties.getTimeZone());
    Map<CountKey, MutableCount> grouped = new HashMap<>();

    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(properties.getBucket()).key(object.key()).build();
    try (ResponseInputStream<GetObjectResponse> input = s3.getObject(request);
         GZIPInputStream gzip = new GZIPInputStream(input);
         BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        parser.parseCountableRequest(line).ifPresent(fact -> resolver.resolve(fact.handle(), fact.occurredAt())
            .ifPresent(academyId -> grouped
                .computeIfAbsent(new CountKey(academyId, fact.occurredAt().atZone(zoneId).toLocalDate()),
                    ignored -> new MutableCount())
                .add(fact.occurredAt())));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read ALB access log object: " + object.key(), e);
    }

    List<AcademyAccessStatisticsImportService.DailyCount> counts = grouped.entrySet().stream()
        .map(entry -> new AcademyAccessStatisticsImportService.DailyCount(
            entry.getKey().academyId(), entry.getKey().date(), entry.getValue().count,
            entry.getValue().lastLogAt))
        .toList();
    importService.importObject(object.key(), object.eTag(), counts);
    log.info("Imported ALB access log: key={}, countedRequests={}", object.key(),
        counts.stream().mapToLong(AcademyAccessStatisticsImportService.DailyCount::count).sum());
  }

  private record CountKey(Long academyId, LocalDate date) {}

  private static final class MutableCount {
    private long count;
    private Instant lastLogAt;
    void add(Instant occurredAt) {
      count++;
      if (lastLogAt == null || occurredAt.isAfter(lastLogAt)) lastLogAt = occurredAt;
    }
  }
}
