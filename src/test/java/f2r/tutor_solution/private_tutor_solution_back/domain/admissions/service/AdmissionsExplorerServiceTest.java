package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** 실제 SQL로 승인 상태·학년도·캠퍼스·학과 연결·빈값을 검증한다. 운영 데이터 사용 안 함. */
class AdmissionsExplorerServiceTest {
    JdbcTemplate jdbc;
    AdmissionsExplorerService service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:explore_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa",""));
        service=new AdmissionsExplorerService(jdbc);
        jdbc.execute("CREATE TABLE admissions_universities(university_id BIGINT PRIMARY KEY,name VARCHAR,region VARCHAR)");
        jdbc.execute("CREATE TABLE admissions_official_materials(material_id BIGINT PRIMARY KEY,university_id BIGINT,academic_year INT,status VARCHAR,title VARCHAR,document_url VARCHAR)");
        jdbc.execute("CREATE TABLE admissions_departments(department_id BIGINT PRIMARY KEY,university_id BIGINT,academic_year INT,status VARCHAR,source_id BIGINT,name VARCHAR,category VARCHAR,quota_info VARCHAR)");
        jdbc.execute("CREATE TABLE admissions_types(admission_type_id BIGINT,university_id BIGINT,academic_year INT,status VARCHAR,source_id BIGINT,name VARCHAR,category VARCHAR,details VARCHAR)");
        jdbc.execute("CREATE TABLE admissions_schedules(schedule_id BIGINT,university_id BIGINT,academic_year INT,status VARCHAR,source_id BIGINT,title VARCHAR,start_date DATE,end_date DATE,note VARCHAR)");
        jdbc.execute("CREATE TABLE admissions_prior_results(prior_result_id BIGINT,university_id BIGINT,academic_year INT,result_year INT,status VARCHAR,source_id BIGINT,department_name VARCHAR,linked_department_name VARCHAR,round_type VARCHAR,group_name VARCHAR,type_name VARCHAR,metric_label VARCHAR,metric_unit VARCHAR,metric_value DECIMAL(12,4),raw_value_text VARCHAR,recruit_count INT,applicant_count INT,competition_rate DECIMAL,extra_admitted_count INT,source_url VARCHAR,evidence_pages VARCHAR)");
        jdbc.update("INSERT INTO admissions_universities VALUES(1,'테스트대(서울)','서울'),(2,'테스트대(분교)','경기')");
        jdbc.update("INSERT INTO admissions_official_materials VALUES(1,1,2027,'ACTIVE','2027 공식요강','https://example.test/guide'),(2,1,2027,'DRAFT','초안','https://example.test/draft'),(3,2,2027,'ACTIVE','분교 요강','https://example.test/campus'),(4,1,2028,'ACTIVE','2028 계획','https://example.test/plan')");
        jdbc.update("INSERT INTO admissions_departments VALUES(10,1,2027,'ACTIVE',1,'컴퓨터공학과','자연',NULL),(11,1,2027,'DRAFT',1,'비승인 학과',NULL,NULL),(12,1,2027,'ACTIVE',2,'초안 출처 학과',NULL,NULL),(13,1,2028,'ACTIVE',4,'계획 학과',NULL,NULL),(14,1,2027,'ACTIVE',3,'캠퍼스 오연결',NULL,NULL),(15,1,2027,'ACTIVE',4,'학년도 오연결',NULL,NULL)");
    }
    void prior(long id,long univ,int resultYear,String status,long source,String dept,String linked,String round) {
        jdbc.update("INSERT INTO admissions_prior_results(prior_result_id,university_id,academic_year,result_year,status,source_id,department_name,linked_department_name,round_type,type_name,metric_label,metric_unit,metric_value,raw_value_text) VALUES(?,?,2027,?,?,?,?,?,?,'교과','최종등록자 70%컷','GRADE',2.31,'2.31*')",id,univ,resultYear,status,source,dept,linked,round);
    }
    @Test void givenDraftCrossCampusAndOtherYears_whenSearching_thenOnlyApprovedSameYearSourceRows() {
        var list=service.departments(2027,1,"");
        assertThat((java.util.List<?>)list.get("items")).hasSize(1);
        assertThat(service.universities(2027)).hasSize(1);
        assertThat(service.departments(2028,1,"").get("items").toString()).contains("계획 학과").doesNotContain("컴퓨터공학과");
        assertThatThrownBy(()->service.detail(2027,12,"ALL")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.detail(2027,14,"ALL")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void givenChangedDepartmentAndMissingValues_whenComparing_thenExactMappingAndOriginalMetricRetained() {
        prior(1,1,2026,"ACTIVE",1,"컴퓨터공학과",null,"SUSI");
        prior(2,1,2026,"ACTIVE",1,"옛학과","컴퓨터공학과","JEONGSI");
        prior(3,1,2026,"DRAFT",1,"컴퓨터공학과",null,"SUSI");
        prior(4,2,2026,"ACTIVE",3,"컴퓨터공학과",null,"SUSI");
        prior(5,1,2025,"ACTIVE",1,"컴퓨터공학과",null,"SUSI");
        prior(6,1,2026,"ACTIVE",2,"컴퓨터공학과",null,"SUSI");
        prior(7,1,2026,"ACTIVE",1,"컴퓨터공学과",null,"SUSI");
        var data=service.detail(2027,10,"ALL");
        assertThat((java.util.List<?>)data.get("priorResults")).hasSize(2);
        assertThat(data.get("priorResults").toString()).contains("2.31*", "최종등록자 70%컷");
        var first=(java.util.Map<?,?>)((java.util.List<?>)data.get("priorResults")).getFirst();
        assertThat(first.get("recruitCount")).isNull();
        assertThat((java.util.List<?>)service.detail(2027,10,"JEONGSI").get("priorResults")).hasSize(1);
        assertThat(data.get("calculationStatus")).isEqualTo("NOT_CONNECTED");
    }
    @Test void givenWildcardAndWrongYear_whenSearching_thenLiteralSearchAndValidation() {
        assertThat((java.util.List<?>)service.departments(2027,1,"%").get("items")).isEmpty();
        assertThatThrownBy(()->service.departments(2026,1,"")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.detail(2027,10,"unknown")).isInstanceOf(ResponseStatusException.class);
    }
}
