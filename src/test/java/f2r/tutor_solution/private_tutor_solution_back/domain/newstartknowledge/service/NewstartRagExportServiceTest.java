package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartRagExportManifest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * RAG 원본 내보내기 회귀 테스트 — 읽기 전용 계약.
 *
 * <p>검증: 스트리밍 JSON 배열 형식, 21개 필드 보존, 건수·SHA-256 정합,
 * ZIP 5개 엔트리(4 JSON + manifest), 비대상 유형 거부.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RAG 원본 내보내기")
class NewstartRagExportServiceTest {

    @Mock
    private NewstartKnowledgeItemRepository knowledgeRepository;
    @Mock
    private NewstartKnowledgeRelationRepository relationRepository;
    @Mock
    private NewstartKnowledgeStatusLogRepository statusLogRepository;

    private ObjectMapper objectMapper;
    private NewstartKnowledgeService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new NewstartKnowledgeService(
                knowledgeRepository, relationRepository, statusLogRepository, objectMapper);
    }

    private NewstartKnowledgeItem item(long id, SourceType type) {
        return NewstartKnowledgeItem.builder()
                .knowledgeId(id)
                .sourceType(type)
                .status(KnowledgeStatus.ACTIVE)
                .category("부실차주 신청")
                .title("제목 " + id)
                .summary("요약 " + id)
                .content("본문 " + id + " — 축약·재작성 없이 원본 그대로")
                .authority("authority")
                .tags("tag1,tag2")
                .sourceUrl("https://cafe.naver.com/ArticleRead.nhn?clubid=31312978&articleid=" + id)
                .sourceDate(LocalDate.of(2026, 1, 15))
                .version(1)
                .confidence("medium")
                .createdBy("admin-f2r")
                .updatedBy("admin-f2r")
                .createdAt(LocalDateTime.of(2026, 10, 4, 7, 13, 24))
                .updatedAt(LocalDateTime.of(2026, 10, 4, 7, 17, 40))
                .build();
    }

    @Test
    @DisplayName("단일 유형: JSON 배열 스트리밍 + 21개 필드 + 건수/SHA-256 정합")
    void exportSingleType() throws Exception {
        // given
        List<NewstartKnowledgeItem> docs = List.of(
                item(1L, SourceType.CAFE_COMMENT), item(2L, SourceType.CAFE_COMMENT), item(3L, SourceType.CAFE_COMMENT));
        when(knowledgeRepository.streamByStatusAndSourceType(KnowledgeStatus.ACTIVE, SourceType.CAFE_COMMENT))
                .thenReturn(docs.stream());

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NewstartRagExportManifest.FileStats stats =
                service.exportRagSourceType(SourceType.CAFE_COMMENT, out);

        // then
        byte[] bytes = out.toByteArray();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(3);
        // 21개 필드 보존 (DTO 기준)
        assertThat(root.get(0).size()).isEqualTo(21);
        assertThat(root.get(0).get("knowledgeId").asLong()).isEqualTo(1L);
        assertThat(root.get(0).get("content").asText()).contains("원본 그대로");
        assertThat(root.get(0).get("sourceType").asText()).isEqualTo("CAFE_COMMENT");
        assertThat(root.get(0).get("status").asText()).isEqualTo("ACTIVE");

        assertThat(stats.getCount()).isEqualTo(3);
        assertThat(stats.getBytes()).isEqualTo(bytes.length);
        String expectedSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(stats.getSha256()).isEqualTo(expectedSha);
        assertThat(stats.getFileName()).isEqualTo("cafe_comment.json");
    }

    @Test
    @DisplayName("단일 유형: 빈 결과는 빈 배열 []")
    void exportSingleTypeEmpty() throws Exception {
        // given
        when(knowledgeRepository.streamByStatusAndSourceType(KnowledgeStatus.ACTIVE, SourceType.EBOOK))
                .thenReturn(Stream.empty());

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NewstartRagExportManifest.FileStats stats = service.exportRagSourceType(SourceType.EBOOK, out);

        // then
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("[]");
        assertThat(stats.getCount()).isZero();
    }

    @Test
    @DisplayName("내보내기 대상이 아닌 유형(CASE 등)은 거부")
    void exportRejectsNonTargetType() {
        // given/when/then
        assertThatThrownBy(() -> service.exportRagSourceType(SourceType.CASE, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveRagExportType("CASE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveRagExportType("RULE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveRagExportType(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유형 문자열 검증: 4종 통과")
    void resolveExportTypeOk() {
        // given/when/then
        assertThat(service.resolveRagExportType("CAFE_COMMENT")).isEqualTo(SourceType.CAFE_COMMENT);
        assertThat(service.resolveRagExportType("CAFE_ARTICLE")).isEqualTo(SourceType.CAFE_ARTICLE);
        assertThat(service.resolveRagExportType("OPERATOR_KNOWLEDGE")).isEqualTo(SourceType.OPERATOR_KNOWLEDGE);
        assertThat(service.resolveRagExportType("EBOOK")).isEqualTo(SourceType.EBOOK);
    }

    @Test
    @DisplayName("전체 ZIP: 4 JSON + manifest, manifest 통계와 실제 엔트리 정합")
    void exportAllZip() throws Exception {
        // given
        Map<SourceType, List<NewstartKnowledgeItem>> docs = new TreeMap<>();
        docs.put(SourceType.CAFE_COMMENT, List.of(item(11L, SourceType.CAFE_COMMENT), item(12L, SourceType.CAFE_COMMENT)));
        docs.put(SourceType.CAFE_ARTICLE, List.of(item(21L, SourceType.CAFE_ARTICLE)));
        docs.put(SourceType.OPERATOR_KNOWLEDGE, List.of(item(31L, SourceType.OPERATOR_KNOWLEDGE)));
        docs.put(SourceType.EBOOK, List.of(item(41L, SourceType.EBOOK), item(42L, SourceType.EBOOK), item(43L, SourceType.EBOOK)));
        for (Map.Entry<SourceType, List<NewstartKnowledgeItem>> e : docs.entrySet()) {
            when(knowledgeRepository.streamByStatusAndSourceType(KnowledgeStatus.ACTIVE, e.getKey()))
                    .thenReturn(e.getValue().stream());
        }

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NewstartRagExportManifest manifest = service.exportRagAll(out);

        // then
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(entries.keySet()).containsExactly(
                "cafe_article.json", "cafe_comment.json", "ebook.json", "manifest.json", "operator_knowledge.json");

        // manifest 파싱 + 통계 정합
        JsonNode manifestJson = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifestJson.get("totalCount").asLong()).isEqualTo(7);
        assertThat(manifestJson.get("files").size()).isEqualTo(4);
        for (JsonNode f : manifestJson.get("files")) {
            String name = f.get("fileName").asText();
            byte[] body = entries.get(name);
            assertThat(f.get("bytes").asLong()).isEqualTo(body.length);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            assertThat(f.get("sha256").asText()).isEqualTo(sha);
            assertThat(f.get("count").asLong()).isEqualTo(objectMapper.readTree(body).size());
        }
        assertThat(manifest.getTotalCount()).isEqualTo(7);
        assertThat(manifest.getTotalBytes()).isEqualTo(
                entries.get("cafe_comment.json").length + entries.get("cafe_article.json").length
                        + entries.get("operator_knowledge.json").length + entries.get("ebook.json").length);
    }

    @Test
    @DisplayName("manifest 미리보기: DB 카운트 기준 4종 + 합계")
    void buildPreview() {
        // given
        when(knowledgeRepository.countByStatusAndSourceType(eq(KnowledgeStatus.ACTIVE), any()))
                .thenReturn(10L);

        // when
        NewstartRagExportManifest preview = service.buildRagExportPreview();

        // then
        assertThat(preview.getFiles()).hasSize(4);
        assertThat(preview.getFiles().stream().map(NewstartRagExportManifest.FileStats::getSourceType))
                .containsExactly("CAFE_COMMENT", "CAFE_ARTICLE", "OPERATOR_KNOWLEDGE", "EBOOK");
        assertThat(preview.getTotalCount()).isEqualTo(40);
        assertThat(preview.getGeneratedAt()).isNotBlank();
    }

    @Test
    @DisplayName("내보내기 순서는 knowledgeId 오름차순 그대로 유지")
    void exportPreservesRepositoryOrder() throws Exception {
        // given — 저장소가 knowledgeId ASC 스트림을 반환한다고 가정
        List<NewstartKnowledgeItem> docs = new ArrayList<>(List.of(
                item(3L, SourceType.CAFE_COMMENT), item(1L, SourceType.CAFE_COMMENT), item(2L, SourceType.CAFE_COMMENT)));
        when(knowledgeRepository.streamByStatusAndSourceType(KnowledgeStatus.ACTIVE, SourceType.CAFE_COMMENT))
                .thenReturn(docs.stream());

        // when
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportRagSourceType(SourceType.CAFE_COMMENT, out);

        // then — 스트리밍은 받은 순서를 그대로 기록 (순서 보장은 저장소 쿼리의 ORDER BY)
        JsonNode root = objectMapper.readTree(out.toByteArray());
        assertThat(root.get(0).get("knowledgeId").asLong()).isEqualTo(3L);
        assertThat(root.get(1).get("knowledgeId").asLong()).isEqualTo(1L);
        assertThat(root.get(2).get("knowledgeId").asLong()).isEqualTo(2L);
    }
}
