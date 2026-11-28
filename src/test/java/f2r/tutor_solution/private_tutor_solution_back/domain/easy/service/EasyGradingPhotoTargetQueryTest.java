package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.JpaConfig;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyGradingPhotoTarget;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyGradingRecordRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class EasyGradingPhotoTargetQueryTest {
  @Autowired EntityManager em;
  @Autowired EasyGradingRecordRepository gradings;

  @Test void givenMixedRecords_whenListingForOneBusiness_thenCorrectCountAndExclusiveCursor() {
    var mine = EasyBusiness.builder().code("A").name("가명 학원").active(true).sortOrder(1).build();
    var other = EasyBusiness.builder().code("B").name("다른 학원").active(true).sortOrder(2).build();
    em.persist(mine);
    em.persist(other);
    var student = EasyStudent.builder().name("가명 학생").grade((byte) 2).build();
    em.persist(student);
    var first = create(mine, student);
    var second = create(mine, student);
    create(other, student);
    em.persist(new EasyGradingPhoto(second, "STUDENT", "private", "image/jpeg", 1024));
    em.flush();
    em.clear();

    var page = PageRequest.of(0, 51);
    var rows = gradings.photoTargets(mine.getBusinessId(), null, page);
    assertThat(rows).extracting(EasyGradingPhotoTarget::gradingId)
        .containsExactly(second.getGradingId(), first.getGradingId());
    assertThat(rows).extracting(EasyGradingPhotoTarget::photoCount).containsExactly(1L, 0L);
    assertThat(gradings.photoTargets(mine.getBusinessId(), second.getGradingId(), page))
        .extracting(EasyGradingPhotoTarget::gradingId).containsExactly(first.getGradingId());
    assertThat(gradings.lockForPhoto(other.getBusinessId(), first.getGradingId())).isEmpty();
    assertThat(gradings.lockForPhoto(mine.getBusinessId(), first.getGradingId())).isPresent();
  }

  private EasyGradingRecord create(EasyBusiness business, EasyStudent student) {
    var record = EasyGradingRecord.builder().business(business).student(student)
        .source("WEB").gradedOn(LocalDate.now()).build();
    em.persist(record);
    return record;
  }
}
