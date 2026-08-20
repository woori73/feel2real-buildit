package f2r.tutor_solution.private_tutor_solution_back.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * service-layer ownership 가드의 회귀 방지.
 *
 * <p>핵심 시나리오: 학생A가 학생B의 conversationId를 알아내 GET /api/conversations/{id} 시도 → 403.
 * 학생A의 자기 conversation 조회는 200. ADMIN의 임퍼소네이트(다른 학생 conversation 접근)는 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ownership Guard 통합 테스트")
class OwnershipGuardIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository userRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired ConversationRepository conversationRepository;
  @Autowired PasswordEncoder passwordEncoder;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Long studentAUserId;
  private Long studentBConversationId;

  @BeforeEach
  void seedScenario() {
    // 학생A — 인증해서 호출자 역할
    User userA = ensureUser("ownership-guard-a", "pw-a-1234", Role.STUDENT);
    Student studentA = ensureStudent(userA);
    studentAUserId = userA.getUserId();

    // 학생B — 호출자가 접근하면 안 될 다른 학생
    User userB = ensureUser("ownership-guard-b", "pw-b-1234", Role.STUDENT);
    Student studentB = ensureStudent(userB);

    // 학생B의 conversation 생성 (학생A가 이 conversationId를 알았다고 가정)
    Conversation convB = Conversation.builder()
        .student(studentB)
        .title("학생B의 대화")
        .build();
    studentBConversationId = conversationRepository.save(convB).getConversationId();
  }

  @Test
  @DisplayName("학생A 토큰으로 학생B의 conversationId 조회 시 403 — service layer ownership 가드")
  void studentACannotReadStudentBConversation() throws Exception {
    // given
    Cookie token = loginAs("ownership-guard-a", "pw-a-1234");

    // when & then
    mockMvc.perform(get("/api/conversations/" + studentBConversationId).cookie(token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("학생A가 자기 conversation은 정상 조회된다 (가드 over-block 없음)")
  void studentACanReadOwnConversation() throws Exception {
    // given — 학생A의 자기 conversation 하나 생성
    Student studentA = studentRepository.findByUser_UserId(studentAUserId).orElseThrow();
    Conversation own = conversationRepository.save(Conversation.builder()
        .student(studentA)
        .title("학생A의 대화")
        .build());
    Cookie token = loginAs("ownership-guard-a", "pw-a-1234");

    // when & then
    mockMvc.perform(get("/api/conversations/" + own.getConversationId()).cookie(token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("ADMIN(admin-f2r) 토큰으로는 학생B의 conversation도 임퍼소네이트로 접근 가능")
  void adminCanReadAnyConversation() throws Exception {
    // given
    Cookie token = loginAs("admin-f2r", "admin-mochi-96");

    // when & then
    mockMvc.perform(get("/api/conversations/" + studentBConversationId).cookie(token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("학생A 토큰으로 학생B 자료 다운로드 시도 시 SourceController에서도 403")
  void studentACannotDownloadStudentBFile() throws Exception {
    // 학생B의 source가 dev-seed로 존재 — 그 source_id를 student token으로 시도하면 403
    // V900 dev-seed의 source_id=1~4가 모두 학생 user_id=3(=student) 소유. 학생A는 다른 user.
    Cookie token = loginAs("ownership-guard-a", "pw-a-1234");
    mockMvc.perform(get("/api/sources/1/file").cookie(token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("학생A 토큰으로 학생B의 source 삭제 시도 시 403 — DELETE도 ownership 가드")
  void studentACannotDeleteStudentBSource() throws Exception {
    Cookie token = loginAs("ownership-guard-a", "pw-a-1234");
    // dev-seed의 source_id=1은 시드 학생 소유. 학생A의 토큰으로는 못 지움.
    mockMvc.perform(delete("/api/sources/1").cookie(token))
        .andExpect(status().isForbidden());
  }

  /* ─── helpers ────────────────────────────────────────────────── */

  private User ensureUser(String loginId, String rawPassword, Role role) {
    return userRepository.findByLoginId(loginId).orElseGet(() ->
        userRepository.save(User.builder()
            .loginId(loginId)
            .password(passwordEncoder.encode(rawPassword))
            .name(loginId)
            .role(role)
            .build()));
  }

  private Student ensureStudent(User user) {
    return studentRepository.findByUser_UserId(user.getUserId()).orElseGet(() ->
        studentRepository.save(Student.builder()
            .user(user)
            .schoolName("테스트고")
            .grade((byte) 1)
            .build()));
  }

  private Cookie loginAs(String loginId, String password) throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("loginId", loginId, "password", password));
    MvcResult result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn();
    Cookie cookie = result.getResponse().getCookie("ACCESS_TOKEN");
    assertThat(cookie).isNotNull();
    return cookie;
  }
}
