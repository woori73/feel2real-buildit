package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrefiaCardsalesAdapterTest {

  private static final String CONN_REF = "crefia:1234567890";

  @Test
  @DisplayName("SPI code matches inactive V983 source")
  void adapterContract() {
    CrefiaCardsalesAdapter adapter = new CrefiaCardsalesAdapter();
    assertThat(adapter.adapterCode()).isEqualTo("CARDSALES");
    assertThat(adapter.adapterVersion()).isEqualTo("v1-crefia");
    assertThat(adapter.configKeys()).containsExactly("merchantNo");
  }

  @Test
  @DisplayName("official endpoint and accounting lifecycle are unverified: fail closed")
  void fetchFailsClosedBeforeDependencies() {
    CrefiaCardsalesClient client = mock(CrefiaCardsalesClient.class);
    CrefiaCredentialProvider credentials = mock(CrefiaCredentialProvider.class);
    CrefiaCardsalesAdapter adapter = new CrefiaCardsalesAdapter(client, credentials);

    assertThatThrownBy(() -> adapter.fetch(CONN_REF, null, 100))
        .isInstanceOfSatisfying(EasyCollectionException.class, e ->
            assertThat(e.errorCode()).isEqualTo(EasyCollectionErrorCode.CONFIGURATION_ERROR))
        .hasMessageContaining("disabled");
    verifyNoInteractions(client, credentials);
  }

  @Test
  @DisplayName("connection reference validation performs no network call")
  void validateConnectionNoNetwork() {
    CrefiaCardsalesClient client = mock(CrefiaCardsalesClient.class);
    CrefiaCardsalesAdapter adapter = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials(null, null));
    adapter.validateConnection(CONN_REF);
    verifyNoInteractions(client);
    assertThatThrownBy(() -> adapter.validateConnection("bad"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("invalid limits are typed INVALID_DATA and never call the provider")
  void invalidLimit() {
    CrefiaCardsalesClient client = mock(CrefiaCardsalesClient.class);
    CrefiaCardsalesAdapter adapter = new CrefiaCardsalesAdapter(client,
        () -> new CrefiaCredentialProvider.Credentials("id", "secret"));
    assertThatThrownBy(() -> adapter.fetch(CONN_REF, null, 5001))
        .isInstanceOfSatisfying(EasyCollectionException.class, e ->
            assertThat(e.errorCode()).isEqualTo(EasyCollectionErrorCode.INVALID_DATA));
    verifyNoInteractions(client);
  }
}
