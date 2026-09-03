package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto;

/**
 * "Wiki 자료에서 찾기" 검색 결과 1건. 기존 Wiki 자료실(모의고사 원본 / 학습자료)의 자료를
 * 사람이 눈으로 확인하고 고를 수 있게 최소 정보만 담는다.
 */
public record LinkableMaterialResponse(
    String materialKind,
    String kindLabel,
    Long materialId,
    String title,
    String meta) {}
