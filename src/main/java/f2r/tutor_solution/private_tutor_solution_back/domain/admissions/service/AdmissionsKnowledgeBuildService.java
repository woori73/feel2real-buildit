package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsOfficialMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsScheduleRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository.AdmissionsTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 구조화 입시 자료(전형·일정·공식자료)를 검색용 지식 항목으로 변환한다.
 *
 * <p>지금까지 지식 항목은 상담 지식베이스({@code loadKnowledgeBase})로만 만들어졌고,
 * 적재된 전형·일정·공식자료 행은 {@code AdmissionsResearchService}의 근거가 되지 못했다.
 * 이 서비스가 그 간극을 메운다: 행 1건 → 지식 항목 1건.
 *
 * <p>규칙:
 * <ul>
 *   <li>생성 항목은 전부 DRAFT로 시작한다. 자동 승격 없음 — 승격은 기존 검수/전체 승격 경로만 쓴다.</li>
 *   <li>멱등하다. dedupKey(전형/일정/자료의 PK 기반)로 기존 항목을 찾고,
 *       본문이 같으면 건드리지 않고, 다르면 내용만 갱신하고 version을 올린다(승격 상태는 보존).</li>
 *   <li>본문에는 출처 자료 제목을 "근거 자료:" 줄로 남겨 근거 추적이 가능하게 한다.</li>
 *   <li>2027은 확정(yearConfirmed=true), 2028은 계획(false)으로 구분한다(연구 서비스 STEP1/STEP2 규칙과 동일).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionsKnowledgeBuildService {

    static final String CREATED_BY = "knowledge-build";

    private final AdmissionsTypeRepository typeRepository;
    private final AdmissionsScheduleRepository scheduleRepository;
    private final AdmissionsOfficialMaterialRepository materialRepository;
    private final AdmissionsKnowledgeItemRepository knowledgeRepository;

    /**
     * @param created 새로 만든 항목 수
     * @param updated 본문이 바뀌어 갱신한 항목 수
     * @param unchanged 본문이 같아 건드리지 않은 항목 수
     * @param changedKnowledgeIds 새로 만들거나 갱신한 항목 ID (재색인 대상)
     */
    public record BuildResult(int created, int updated, int unchanged, List<Long> changedKnowledgeIds) {}

    /** 전형·일정·공식자료 전체 행을 지식 항목으로 변환·저장한다. */
    @Transactional
    public BuildResult buildAll() {
        Counters counters = new Counters();
        List<Long> changedIds = new ArrayList<>();

        for (AdmissionsType t : typeRepository.findAll()) {
            apply(fromType(t), counters, changedIds);
        }
        for (AdmissionsSchedule s : scheduleRepository.findAll()) {
            apply(fromSchedule(s), counters, changedIds);
        }
        for (AdmissionsOfficialMaterial m : materialRepository.findAll()) {
            apply(fromMaterial(m), counters, changedIds);
        }

        log.info("admissions knowledge build: created={} updated={} unchanged={}",
                counters.created, counters.updated, counters.unchanged);
        return new BuildResult(counters.created, counters.updated, counters.unchanged, changedIds);
    }

    // ── 행 → 항목 변환 ───────────────────────────────────────────────

    private AdmissionsKnowledgeItem fromType(AdmissionsType t) {
        String univName = universityName(t.getUniversity());
        String title = univName + " " + t.getAcademicYear() + "학년도 " + t.getName();
        StringBuilder content = new StringBuilder(title);
        if (t.getDetails() != null && !t.getDetails().isBlank()) {
            content.append("\n").append(t.getDetails());
        }
        appendSourceLine(content, t.getSource());
        String tags = "전형";
        if (t.getName() != null && t.getName().contains("세종캠퍼스")) {
            tags += " 세종캠퍼스";
        }
        return baseItem(t.getAcademicYear(), title, content.toString(),
                t.getCategory() != null && !t.getCategory().isBlank() ? t.getCategory() : "전형",
                tags, t.getSource(),
                "type:" + universityId(t.getUniversity()) + ":" + t.getAcademicYear() + ":" + t.getAdmissionTypeId());
    }

    private AdmissionsKnowledgeItem fromSchedule(AdmissionsSchedule s) {
        String univName = universityName(s.getUniversity());
        String title = univName + " " + s.getAcademicYear() + "학년도 일정: " + s.getTitle();
        StringBuilder content = new StringBuilder(title);
        if (s.getStartDate() != null || s.getEndDate() != null) {
            content.append("\n기간: ")
                    .append(s.getStartDate() == null ? "" : s.getStartDate().toString())
                    .append(" ~ ")
                    .append(s.getEndDate() == null ? "" : s.getEndDate().toString());
        }
        if (s.getNote() != null && !s.getNote().isBlank()) {
            content.append("\n일정 원문: ").append(s.getNote());
        }
        appendSourceLine(content, s.getSource());
        String tags = "일정";
        if (s.getTitle() != null && s.getTitle().contains("세종캠퍼스")) {
            tags += " 세종캠퍼스";
        }
        return baseItem(s.getAcademicYear(), title, content.toString(), "일정",
                tags, s.getSource(),
                "schedule:" + universityId(s.getUniversity()) + ":" + s.getAcademicYear() + ":" + s.getScheduleId());
    }

    private AdmissionsKnowledgeItem fromMaterial(AdmissionsOfficialMaterial m) {
        String univName = universityName(m.getUniversity());
        String title = univName + " " + m.getAcademicYear() + "학년도 공식자료: " + m.getTitle();
        StringBuilder content = new StringBuilder(title);
        content.append("\n자료명: ").append(m.getTitle());
        if (m.getIssuer() != null && !m.getIssuer().isBlank()) {
            content.append("\n발행처: ").append(m.getIssuer());
        }
        if (m.getPublishDate() != null) {
            content.append("\n발행일: ").append(m.getPublishDate());
        }
        if (m.getDocumentUrl() != null && !m.getDocumentUrl().isBlank()) {
            content.append("\n원문 URL: ").append(m.getDocumentUrl());
        }
        return baseItem(m.getAcademicYear(), title, content.toString(), "공식자료",
                "공식자료", m, "material:" + m.getMaterialId());
    }

    private AdmissionsKnowledgeItem baseItem(Integer academicYear, String title, String content,
                                             String category, String tags,
                                             AdmissionsOfficialMaterial source, String dedupKey) {
        return AdmissionsKnowledgeItem.builder()
                .academicYear(academicYear)
                .yearConfirmed(academicYear != null && academicYear <= 2027)
                .title(title)
                .content(content)
                .category(category)
                .tags(tags)
                .sourceClass(AdmissionsKnowledgeItem.SourceClass.OFFICIAL)
                .source(source)
                .sourceUrl(source != null ? source.getDocumentUrl() : null)
                .status(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT)
                .role(AdmissionsKnowledgeItem.Role.FACT)
                .dedupKey(dedupKey)
                .createdBy(CREATED_BY)
                .build();
    }

    // ── 멱등 upsert ──────────────────────────────────────────────────

    private void apply(AdmissionsKnowledgeItem candidate, Counters counters, List<Long> changedIds) {
        var existing = knowledgeRepository.findByDedupKey(candidate.getDedupKey());
        if (existing.isEmpty()) {
            AdmissionsKnowledgeItem saved = knowledgeRepository.save(candidate);
            counters.created++;
            changedIds.add(saved.getKnowledgeId());
            return;
        }
        AdmissionsKnowledgeItem item = existing.get();
        if (Objects.equals(item.getContent(), candidate.getContent())) {
            counters.unchanged++;
            return;
        }
        item.setTitle(candidate.getTitle());
        item.setContent(candidate.getContent());
        item.setCategory(candidate.getCategory());
        item.setTags(candidate.getTags());
        item.setSource(candidate.getSource());
        item.setSourceUrl(candidate.getSourceUrl());
        item.setVersion((item.getVersion() == null ? 1 : item.getVersion()) + 1);
        item.setUpdatedBy(CREATED_BY);
        knowledgeRepository.save(item);
        counters.updated++;
        changedIds.add(item.getKnowledgeId());
    }

    private static void appendSourceLine(StringBuilder content, AdmissionsOfficialMaterial source) {
        if (source != null) {
            content.append("\n근거 자료: ").append(source.getTitle());
        }
    }

    private static String universityName(AdmissionsUniversity u) {
        return u != null && u.getName() != null ? u.getName() : "대학 미상";
    }

    private static Long universityId(AdmissionsUniversity u) {
        return u != null ? u.getUniversityId() : 0L;
    }

    private static class Counters {
        int created;
        int updated;
        int unchanged;
    }
}
