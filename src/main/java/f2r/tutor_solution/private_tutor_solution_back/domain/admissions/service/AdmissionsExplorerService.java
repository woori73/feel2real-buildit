package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 승인된 공식 행을 그대로 읽는 탐색기. 예측·점수 환산·LLM 호출·운영 쓰기 없음. */
@Service
@Transactional(readOnly = true)
public class AdmissionsExplorerService {
    private final JdbcTemplate jdbc;
    public AdmissionsExplorerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> universities(int year) {
        year(year);
        return jdbc.queryForList("""
            SELECT u.university_id AS id, u.name AS name, u.region AS region
            FROM admissions_universities u
            WHERE EXISTS (SELECT 1 FROM admissions_departments d
              JOIN admissions_official_materials m ON m.material_id=d.source_id
              WHERE d.university_id=u.university_id AND d.academic_year=? AND d.status='ACTIVE'
                AND m.status='ACTIVE' AND m.academic_year=d.academic_year
                AND m.university_id=u.university_id)
            ORDER BY u.name, u.university_id
            """, year);
    }

    public Map<String, Object> departments(int year, long universityId, String query) {
        year(year);
        String q = query == null ? "" : query.strip();
        if (universityId < 1 || q.length() > 100) throw bad("대학과 검색어를 확인해 주세요.");
        // LIKE 문법을 입력받지 않는다. %, _ 역시 문자로 검색한다.
        String like = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var rows = jdbc.queryForList("""
            SELECT d.department_id AS id, d.name AS name, d.category AS category,
                   d.quota_info AS quotaInfo, u.name AS universityName, u.university_id AS universityId,
                   m.title AS sourceTitle, m.document_url AS sourceUrl
            FROM admissions_departments d
            JOIN admissions_universities u ON u.university_id=d.university_id
            JOIN admissions_official_materials m ON m.material_id=d.source_id
            WHERE d.university_id=? AND d.academic_year=? AND d.status='ACTIVE'
              AND m.status='ACTIVE' AND m.academic_year=d.academic_year AND m.university_id=d.university_id
              AND d.name LIKE ? ESCAPE '!'
            ORDER BY d.name, d.department_id LIMIT 81
            """, universityId, year, like);
        return Map.of("academicYear", year, "items", rows.stream().limit(80).toList(), "hasMore", rows.size() > 80);
    }

    public Map<String, Object> detail(int year, long departmentId, String round) {
        year(year);
        if (departmentId < 1 || !("ALL".equals(round) || "SUSI".equals(round) || "JEONGSI".equals(round)))
            throw bad("모집단위와 수시·정시 선택을 확인해 주세요.");
        var rows = jdbc.queryForList("""
            SELECT d.department_id AS id, d.name AS name, d.category AS category,
                   d.quota_info AS quotaInfo, u.name AS universityName, u.university_id AS universityId,
                   m.title AS sourceTitle, m.document_url AS sourceUrl
            FROM admissions_departments d
            JOIN admissions_universities u ON u.university_id=d.university_id
            JOIN admissions_official_materials m ON m.material_id=d.source_id
            WHERE d.department_id=? AND d.academic_year=? AND d.status='ACTIVE'
              AND m.status='ACTIVE' AND m.academic_year=d.academic_year AND m.university_id=d.university_id
            """, departmentId, year);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "승인된 모집단위 자료가 없습니다.");
        var department = rows.getFirst();
        long universityId = ((Number) department.get("universityId")).longValue();
        String departmentName = (String) department.get("name");
        // 같은 대학 ID·상담 학년도·지난해 결과만. 학과명은 검증된 연결명 또는 정확히 같은 원문명.
        var prior = jdbc.queryForList("""
            SELECT p.result_year AS resultYear, p.round_type AS roundType, p.group_name AS groupName,
                   p.type_name AS typeName, p.department_name AS departmentName,
                   p.metric_label AS metricLabel, p.metric_unit AS metricUnit,
                   p.metric_value AS metricValue, p.raw_value_text AS rawValueText,
                   p.recruit_count AS recruitCount, p.applicant_count AS applicantCount,
                   p.competition_rate AS competitionRate, p.extra_admitted_count AS extraAdmittedCount,
                   p.source_url AS sourceUrl, m.document_url AS documentUrl, m.title AS sourceTitle,
                   p.evidence_pages AS evidencePages
            FROM admissions_prior_results p
            JOIN admissions_official_materials m ON m.material_id=p.source_id
            WHERE p.university_id=? AND p.academic_year=? AND p.result_year=? AND p.status='ACTIVE'
              AND m.status='ACTIVE' AND m.university_id=p.university_id AND m.academic_year=p.academic_year
              AND (p.linked_department_name=? OR
                   ((p.linked_department_name IS NULL OR p.linked_department_name='') AND p.department_name=?))
              AND (?='ALL' OR p.round_type=? OR p.round_type='OTHER')
            ORDER BY p.round_type, p.group_name, p.type_name, p.metric_label, p.prior_result_id LIMIT 201
            """, universityId, year, year - 1, departmentName, departmentName, round, round);
        // 대학 전체 전형·일정이다. 이 모집단위에 적용되는 전형으로 단정하지 않는다.
        var types = jdbc.queryForList("""
            SELECT t.name AS name, t.category AS category, t.details AS details,
                   m.title AS sourceTitle, m.document_url AS sourceUrl
            FROM admissions_types t JOIN admissions_official_materials m ON m.material_id=t.source_id
            WHERE t.university_id=? AND t.academic_year=? AND t.status='ACTIVE'
              AND m.status='ACTIVE' AND m.university_id=t.university_id AND m.academic_year=t.academic_year
            ORDER BY t.name, t.admission_type_id LIMIT 61
            """, universityId, year);
        var schedules = jdbc.queryForList("""
            SELECT s.title AS title, s.start_date AS startDate, s.end_date AS endDate, s.note AS note,
                   m.title AS sourceTitle, m.document_url AS sourceUrl
            FROM admissions_schedules s JOIN admissions_official_materials m ON m.material_id=s.source_id
            WHERE s.university_id=? AND s.academic_year=? AND s.status='ACTIVE'
              AND m.status='ACTIVE' AND m.university_id=s.university_id AND m.academic_year=s.academic_year
            ORDER BY s.start_date, s.schedule_id LIMIT 81
            """, universityId, year);
        return Map.of("academicYear", year, "department", department,
                "priorResults", prior.stream().limit(200).toList(), "priorResultsTruncated", prior.size() > 200,
                "universityTypes", types.stream().limit(60).toList(), "typesTruncated", types.size() > 60,
                "universitySchedules", schedules.stream().limit(80).toList(), "schedulesTruncated", schedules.size() > 80,
                "calculationStatus", year == 2027 ? "API_AVAILABLE" : "UNSUPPORTED_YEAR");
    }

    private static void year(int year) {
        if (year != 2027 && year != 2028) throw bad("학년도는 2027 또는 2028을 선택해 주세요.");
    }
    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
