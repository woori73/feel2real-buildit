package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 원본 내보내기 manifest — ZIP 내부 {@code manifest.json} 및
 * {@code GET /api/admin/newstart/wiki/export/manifest} 응답 공용 DTO.
 * 읽기 전용 스냅샷이며 DB를 변경하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewstartRagExportManifest {

    /** ISO-8601 생성 시각 (서버 기준). */
    private String generatedAt;

    /** 유형별 파일 통계 (내보내기 고정 순서). */
    private List<FileStats> files;

    /** 전체 문서 수 (4개 유형 합계). */
    private long totalCount;

    /** 전체 바이트 수 (4개 JSON 합계, manifest 제외). */
    private long totalBytes;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileStats {
        private String sourceType;
        private String fileName;
        private long count;
        private long bytes;
        private String sha256;
    }

    public static NewstartRagExportManifest of(String generatedAt, List<FileStats> files) {
        long totalCount = files.stream().mapToLong(FileStats::getCount).sum();
        long totalBytes = files.stream().mapToLong(FileStats::getBytes).sum();
        return NewstartRagExportManifest.builder()
                .generatedAt(generatedAt)
                .files(List.copyOf(files))
                .totalCount(totalCount)
                .totalBytes(totalBytes)
                .build();
    }
}
