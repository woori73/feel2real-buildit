package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
final class AssistantJson {
  private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
  static String write(Object value) { try { return JSON.writeValueAsString(value); } catch(Exception e) { throw new IllegalArgumentException("초안 형식을 확인하세요.",e); } }
  static JsonNode read(String value) { try { return JSON.readTree(value); } catch(Exception e) { throw new IllegalArgumentException("JSON 형식을 확인하세요.",e); } }
  static Object plain(Object value) { try { return JSON.readValue(JSON.writeValueAsString(value),Object.class); } catch(Exception e) { throw new IllegalArgumentException("응답 형식을 확인하세요.",e); } }
  static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
