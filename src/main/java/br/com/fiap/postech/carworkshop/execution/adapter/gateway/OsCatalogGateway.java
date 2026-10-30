package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.exception.CatalogUnavailableException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidDiagnosisException;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.CatalogValidationPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Classe comum, nao bean: o timeout, o numero de tentativas e os limiares do circuito entram pelo construtor,
// o que permite exercita-los por contagem de chamadas num teste puro. Com anotacoes de fault tolerance a mesma
// prova exigiria subir a aplicacao.
public class OsCatalogGateway implements CatalogValidationPort {

    private static final Logger log = LoggerFactory.getLogger(OsCatalogGateway.class);

    private static final String PATH = "/carworkshop/v1/internal/catalog/validate";
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_INVALID = "invalid";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_CATALOG_ID = "catalogId";

    private final String baseUrl;
    private final String serviceToken;
    private final Duration timeout;
    private final int maxAttempts;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuit;
    private final Clock clock;

    public OsCatalogGateway(String baseUrl, String serviceToken, Duration timeout, int maxAttempts,
                            HttpClient httpClient, ObjectMapper objectMapper, CircuitBreaker circuit,
                            Clock clock) {
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.circuit = circuit;
        this.clock = clock;
    }

    @Override
    public void assertKnown(List<DiagnosedItem> items) {
        if (items.isEmpty()) {
            return;
        }
        if (circuit.isOpen(Instant.now(clock))) {
            throw new CatalogUnavailableException("The catalog is unreachable: the circuit is open.");
        }
        List<String> invalid = invalidItemsOf(send(bodyFor(items)));
        if (!invalid.isEmpty()) {
            throw new InvalidDiagnosisException("The catalog does not know " + String.join(", ", invalid) + ".");
        }
    }

    private String bodyFor(List<DiagnosedItem> items) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode entries = body.putArray(FIELD_ITEMS);
        for (DiagnosedItem item : items) {
            ObjectNode entry = entries.addObject();
            entry.put(FIELD_TYPE, item.type());
            entry.put(FIELD_CATALOG_ID, item.catalogId());
        }
        return body.toString();
    }

    private List<String> invalidItemsOf(JsonNode response) {
        List<String> invalid = new ArrayList<>();
        JsonNode entries = response.path(FIELD_INVALID);
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                invalid.add(entry.path(FIELD_TYPE).asText("?") + "/" + entry.path(FIELD_CATALOG_ID).asText("?"));
            }
        }
        return invalid;
    }

    // Retry so para 5xx e falha de transporte: 4xx e resposta do OS, nao instabilidade, e retentar nao muda o
    // resultado. 401 e segredo errado do nosso lado - nao vira 400 para o mecanico.
    private JsonNode send(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(SERVICE_TOKEN_HEADER, serviceToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        CatalogUnavailableException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    circuit.recordSuccess();
                    return parse(response.body());
                }
                if (response.statusCode() / 100 != 5) {
                    return refuse("the catalog answered HTTP " + response.statusCode());
                }
                last = new CatalogUnavailableException("The catalog answered HTTP " + response.statusCode()
                        + ".");
            } catch (IOException e) {
                last = new CatalogUnavailableException("The catalog is unreachable: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return refuse("the catalog call was interrupted");
            }
        }
        circuit.recordFailure(Instant.now(clock));
        throw last;
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return refuse("the catalog returned a malformed body");
        }
    }

    private JsonNode refuse(String reason) {
        circuit.recordFailure(Instant.now(clock));
        log.error("Catalog validation refused: {}.", reason);
        throw new CatalogUnavailableException("The catalog could not validate the items.");
    }
}
