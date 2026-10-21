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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdmissionsKnowledgeBuildService 단위 테스트 — 구조화 행 → 지식 항목 변환·멱등성.
 * given/when/then 구조. 리포지토리는 인메모리 맵으로 흉내 낸다.
 */
class AdmissionsKnowledgeBuildServiceTest {

    private AdmissionsTypeRepository typeRepository;
    private AdmissionsScheduleRepository scheduleRepository;
    private AdmissionsOfficialMaterialRepository materialRepository;
    private AdmissionsKnowledgeItemRepository knowledgeRepository;
    private AdmissionsKnowledgeBuildService service;

    private final Map<String, AdmissionsKnowledgeItem> store = new HashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    private AdmissionsUniversity korea;
    private AdmissionsOfficialMaterial sejongMaterial;
    private AdmissionsType sejongType;
    private AdmissionsSchedule schedule;

    @BeforeEach
    void setUp() {
        typeRepository = mock(AdmissionsTypeRepository.class);
        scheduleRepository = mock(AdmissionsScheduleRepository.class);
        materialRepository = mock(AdmissionsOfficialMaterialRepository.class);
        knowledgeRepository = mock(AdmissionsKnowledgeItemRepository.class);
        service = new AdmissionsKnowledgeBuildService(
                typeRepository, scheduleRepository, materialRepository, knowledgeRepository);

        korea = AdmissionsUniversity.builder().universityId(10L).name("고려대학교").build();
        sejongMaterial = AdmissionsOfficialMaterial.builder()
                .materialId(100L)
                .university(korea)
                .academicYear(2027)
                .title("고려대학교(세종캠퍼스) 2027학년도 수시모집요강")
                .issuer("고려대학교 입학처")
                .documentUrl("https://example.test/korea-susi")
                .build();
        sejongType = AdmissionsType.builder()
                .admissionTypeId(1000L)
                .university(korea)
                .academicYear(2027)
                .name("[세종캠퍼스] 학생부교과(일반전형)")
                .category(null)
                .details("모집인원: \n지원자격: \n전형방법: 일괄, 학생부(교과) 100%\n수능최저: 국어, 수학, 영어, 탐구 중 2개 영역 등급 합 6등급 이내")
                .source(sejongMaterial)
                .build();
        schedule = AdmissionsSchedule.builder()
                .scheduleId(2000L)
                .university(korea)
                .academicYear(2027)
                .title("원서접수 (수시)")
                .note("2026. 9. 7.(월) 10:00 ~ 9. 9.(수)")
                .source(sejongMaterial)
                .build();

        when(typeRepository.findAll()).thenReturn(List.of(sejongType));
        when(scheduleRepository.findAll()).thenReturn(List.of(schedule));
        when(materialRepository.findAll()).thenReturn(List.of(sejongMaterial));
        when(knowledgeRepository.findByDedupKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(knowledgeRepository.save(any(AdmissionsKnowledgeItem.class))).thenAnswer(inv -> {
            AdmissionsKnowledgeItem item = inv.getArgument(0);
            if (item.getKnowledgeId() == null) {
                item.setKnowledgeId(idSeq.incrementAndGet());
            }
            store.put(item.getDedupKey(), item);
            return item;
        });
    }

    @Test
    @DisplayName("항목 생성 — 전형·일정·자료 각 1건, dedupKey·근거 줄·캠퍼스 태그·확정 구분")
    void build_createsItemsFromStructuredRows() {
        // when
        var result = service.buildAll();

        // then
        assertEquals(3, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.unchanged());
        assertEquals(3, result.changedKnowledgeIds().size());

        AdmissionsKnowledgeItem typeItem = store.get("type:10:2027:1000");
        assertNotNull(typeItem, "전형 항목 dedupKey");
        assertEquals("고려대학교 2027학년도 [세종캠퍼스] 학생부교과(일반전형)", typeItem.getTitle());
        assertTrue(typeItem.getContent().contains("수능최저: 국어, 수학, 영어, 탐구 중 2개 영역 등급 합 6등급 이내"),
                "본문에 전형 details 포함: " + typeItem.getContent());
        assertTrue(typeItem.getContent().contains("근거 자료: 고려대학교(세종캠퍼스) 2027학년도 수시모집요강"),
                "본문에 근거 자료 줄 포함");
        assertEquals("전형", typeItem.getCategory(), "category가 null이면 '전형'으로 대체");
        assertTrue(typeItem.getTags().contains("세종캠퍼스"), "세종캠퍼스 태그: " + typeItem.getTags());
        assertEquals(AdmissionsKnowledgeItem.SourceClass.OFFICIAL, typeItem.getSourceClass());
        assertEquals(AdmissionsKnowledgeItem.Role.FACT, typeItem.getRole());
        assertEquals(AdmissionsKnowledgeItem.KnowledgeStatus.DRAFT, typeItem.getStatus(), "새 항목은 DRAFT");
        assertEquals(Boolean.TRUE, typeItem.getYearConfirmed(), "2027은 확정");
        assertEquals("https://example.test/korea-susi", typeItem.getSourceUrl());
        assertSame(sejongMaterial, typeItem.getSource());

        AdmissionsKnowledgeItem schedItem = store.get("schedule:10:2027:2000");
        assertNotNull(schedItem, "일정 항목 dedupKey");
        assertEquals("고려대학교 2027학년도 일정: 원서접수 (수시)", schedItem.getTitle());
        assertTrue(schedItem.getContent().contains("2026. 9. 7.(월) 10:00 ~ 9. 9.(수)"),
                "일정 원문(note) 포함: " + schedItem.getContent());
        assertEquals("일정", schedItem.getCategory());

        AdmissionsKnowledgeItem matItem = store.get("material:100");
        assertNotNull(matItem, "자료 개요 항목 dedupKey");
        assertEquals("고려대학교 2027학년도 공식자료: 고려대학교(세종캠퍼스) 2027학년도 수시모집요강", matItem.getTitle());
        assertTrue(matItem.getContent().contains("발행처: 고려대학교 입학처"));
        assertTrue(matItem.getContent().contains("원문 URL: https://example.test/korea-susi"));
        assertEquals("공식자료", matItem.getCategory());
    }

    @Test
    @DisplayName("멱등 — 같은 입력으로 다시 실행하면 전부 unchanged")
    void build_rerunIsIdempotent() {
        // given
        service.buildAll();

        // when
        var second = service.buildAll();

        // then
        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(3, second.unchanged());
        assertTrue(second.changedKnowledgeIds().isEmpty());
        assertEquals(1, store.get("type:10:2027:1000").getVersion(), "버전 그대로");
    }

    @Test
    @DisplayName("갱신 — 전형 details가 바뀌면 해당 항목만 갱신하고 version 증가, 승격 상태는 보존")
    void build_contentChangedUpdatesOnlyThatItem() {
        // given
        service.buildAll();
        AdmissionsKnowledgeItem typeItem = store.get("type:10:2027:1000");
        typeItem.setStatus(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE); // 이미 승격된 상태라고 가정
        sejongType.setDetails("모집인원: \n지원자격: \n전형방법: 일괄, 학생부(교과) 100%\n수능최저: 변경된 기준 — 2개 영역 등급 합 5등급 이내");

        // when
        var result = service.buildAll();

        // then
        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(2, result.unchanged());
        assertEquals(List.of(typeItem.getKnowledgeId()), result.changedKnowledgeIds());
        assertEquals(2, typeItem.getVersion());
        assertTrue(typeItem.getContent().contains("변경된 기준"), "새 본문 반영");
        assertEquals(AdmissionsKnowledgeItem.KnowledgeStatus.ACTIVE, typeItem.getStatus(), "승격 상태 보존");
    }
}
