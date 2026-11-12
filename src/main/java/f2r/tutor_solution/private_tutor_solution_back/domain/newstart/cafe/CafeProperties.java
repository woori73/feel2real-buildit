package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

/** 별도 네이버 앱. 기존 회원용 네이버 로그인의 권한·토큰 정책을 바꾸지 않는다. */
@Component
@ConfigurationProperties(prefix = "app.newstart.cafe")
@Getter
@Setter
public class CafeProperties {
  private boolean enabled = false;
  private String publicBaseUrl = "https://feel2real.co.kr";
  private String clientId = "";
  private String clientSecret = "";
  private String tokenKeyBase64 = "";

  public String base() {
    return publicBaseUrl.replaceAll("/+$", "");
  }

  public String callback() {
    return base() + "/admin/newstart/cafe/callback";
  }
}
