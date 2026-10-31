package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.exception.CatalogUnavailableException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidDiagnosisException;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsCatalogGatewayTest {

    private static final String PATH = "/carworkshop/v1/internal/catalog/validate";
    private static final String TOKEN = "test-service-token";

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void acceptsItemsTheCatalogKnowsAndSendsTheServiceToken() {
        stub(200, "{\"invalid\":[]}");

        assertDoesNotThrow(() -> gateway(2, 3).assertKnown(ExecutionFixtures.diagnosedItems()));

        wireMock.verify(postRequestedFor(urlPathEqualTo(PATH))
                .withHeader("X-Service-Token", equalTo(TOKEN))
                .withRequestBody(equalToJson("{\"items\":[{\"type\":\"SERVICE\",\"catalogId\":7},"
                        + "{\"type\":\"PART\",\"catalogId\":12}]}")));
    }

    @Test
    void doesNotCallTheCatalogWhenThereIsNothingToValidate() {
        assertDoesNotThrow(() -> gateway(2, 3).assertKnown(List.of()));

        wireMock.verify(0, postRequestedFor(urlPathEqualTo(PATH)));
    }

    // Item que o catalogo nao conhece e resposta de negocio: volta como 400 ao mecanico, nomeando o item, e nao
    // entra na saga.
    @Test
    void refusesAnItemTheCatalogDoesNotKnow() {
        stub(200, "{\"invalid\":[{\"type\":\"PART\",\"catalogId\":99}]}");

        InvalidDiagnosisException thrown = assertThrows(InvalidDiagnosisException.class,
                () -> gateway(2, 3).assertKnown(List.of(new DiagnosedItem(DiagnosedItem.PART, 99L, 1))));

        assertTrue(thrown.getMessage().contains("PART/99"));
    }

    @Test
    void retriesAServerErrorAndThenReportsTheCatalogUnavailable() {
        stub(503, "indisponivel");

        assertThrows(CatalogUnavailableException.class,
                () -> gateway(3, 5).assertKnown(ExecutionFixtures.diagnosedItems()));

        wireMock.verify(3, postRequestedFor(urlPathEqualTo(PATH)));
    }

    // 4xx nao e instabilidade: nao se retenta. 401 significa segredo errado do nosso lado, e vira 503 ao
    // mecanico em vez de 400, porque o diagnostico dele nao tem defeito nenhum.
    @Test
    void doesNotRetryARefusalAndStillReportsItUnavailable() {
        stub(401, "");

        assertThrows(CatalogUnavailableException.class,
                () -> gateway(3, 5).assertKnown(ExecutionFixtures.diagnosedItems()));

        wireMock.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void reportsTheCatalogUnavailableOnATimeout() {
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(1500).withBody("{\"invalid\":[]}")));

        assertThrows(CatalogUnavailableException.class, () -> new OsCatalogGateway(baseUrl(), TOKEN,
                Duration.ofMillis(150), 1, HttpClient.newHttpClient(), new ObjectMapper(),
                new CircuitBreaker(5, Duration.ofSeconds(30)), fixedClock())
                .assertKnown(ExecutionFixtures.diagnosedItems()));
    }

    @Test
    void reportsTheCatalogUnavailableOnAMalformedBody() {
        stub(200, "nao e json");

        assertThrows(CatalogUnavailableException.class,
                () -> gateway(1, 5).assertKnown(ExecutionFixtures.diagnosedItems()));
    }

    // Circuito aberto responde 503 sem chamar: e o que impede o OS caido de virar fila de chamadas presas.
    @Test
    void stopsCallingOnceTheCircuitOpens() {
        stub(503, "indisponivel");
        OsCatalogGateway gateway = gateway(1, 2);

        assertThrows(CatalogUnavailableException.class,
                () -> gateway.assertKnown(ExecutionFixtures.diagnosedItems()));
        assertThrows(CatalogUnavailableException.class,
                () -> gateway.assertKnown(ExecutionFixtures.diagnosedItems()));
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(PATH)));

        assertThrows(CatalogUnavailableException.class,
                () -> gateway.assertKnown(ExecutionFixtures.diagnosedItems()));

        wireMock.verify(2, postRequestedFor(urlPathEqualTo(PATH)));
    }

    private void stub(int status, String body) {
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private OsCatalogGateway gateway(int maxAttempts, int failureThreshold) {
        return new OsCatalogGateway(baseUrl(), TOKEN, Duration.ofSeconds(5), maxAttempts,
                HttpClient.newHttpClient(), new ObjectMapper(),
                new CircuitBreaker(failureThreshold, Duration.ofSeconds(30)), fixedClock());
    }

    private String baseUrl() {
        return "http://localhost:" + wireMock.port();
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
    }
}
