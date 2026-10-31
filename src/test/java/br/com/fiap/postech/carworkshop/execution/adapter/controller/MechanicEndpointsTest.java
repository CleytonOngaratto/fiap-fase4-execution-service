package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.support.TestTokens;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class MechanicEndpointsTest {

    @InjectMock
    MechanicUseCase useCase;

    @Test
    void refusesTheQueueWithoutAToken() {
        given().when().get("/queue?branchId=SP-01").then().statusCode(401);
    }

    @Test
    void refusesTheQueueToACustomer() {
        given().auth().oauth2(TestTokens.customer())
                .when().get("/queue?branchId=SP-01")
                .then().statusCode(403);
    }

    @Test
    void readsTheQueueOfABranchWithAnAdminToken() {
        when(useCase.queue(anyString(), any(ExecutionStatus.class), anyInt()))
                .thenReturn(List.of(ExecutionFixtures.queued()));

        given().auth().oauth2(TestTokens.admin())
                .when().get("/queue?branchId=SP-01")
                .then().statusCode(200)
                .body("[0].workOrderId", is(42))
                .body("[0].status", is("DIAGNOSIS_QUEUED"))
                .body("[0].branchId", is("SP-01"));
    }

    @Test
    void refusesTheQueueWithoutABranchOrWithAnUnknownStatus() {
        given().auth().oauth2(TestTokens.admin())
                .when().get("/queue").then().statusCode(400);
        given().auth().oauth2(TestTokens.admin())
                .when().get("/queue?branchId=SP-01&status=PAINTING").then().statusCode(400);
        given().auth().oauth2(TestTokens.admin())
                .when().get("/queue?branchId=SP-01&limit=0").then().statusCode(400);
    }

    // A prova da inversao medida entre os dois tokens: o real traz so "sub" e o de teste do OS traz so "upn".
    // getName() e o que devolve o mecanico nas duas formas - uma claim fixa daria nulo num dos lados.
    @Test
    void takesTheMechanicFromTheTokenItself() {
        given().auth().oauth2(TestTokens.admin()).contentType("application/json")
                .when().post("/work-orders/42/diagnosis/start")
                .then().statusCode(202);

        ArgumentCaptor<String> mechanic = ArgumentCaptor.forClass(String.class);
        verify(useCase).startDiagnosis(anyLong(), mechanic.capture(), any());
        assertEquals(ExecutionFixtures.MECHANIC, mechanic.getValue());
    }

    @Test
    void acceptsEveryMechanicActionWithAnAdminToken() {
        accept("/work-orders/42/diagnosis/start", null);
        accept("/work-orders/42/diagnosis/complete",
                "{\"description\":\"Amortecedor gasto\",\"items\":[{\"type\":\"PART\",\"catalogId\":12,"
                        + "\"quantity\":2}]}");
        accept("/work-orders/42/diagnosis/fail", "{\"reason\":\"sem reparo viavel\"}");
        accept("/work-orders/42/repair/start", null);
        accept("/work-orders/42/repair/complete", null);
        accept("/work-orders/42/repair/fail", "{\"reason\":\"parafuso quebrou\"}");
    }

    @Test
    void refusesEveryMechanicActionWithoutAToken() {
        given().contentType("application/json")
                .when().post("/work-orders/42/diagnosis/start").then().statusCode(401);
        given().contentType("application/json").body("{\"reason\":\"x\"}")
                .when().post("/work-orders/42/repair/fail").then().statusCode(401);
    }

    @Test
    void refusesAFailureWithoutAReason() {
        given().auth().oauth2(TestTokens.admin()).contentType("application/json").body("{}")
                .when().post("/work-orders/42/diagnosis/fail").then().statusCode(400);
    }

    // O smoke da borda bate em /info sem token: ligar a seguranca nao pode fechar essa rota.
    @Test
    void keepsTheInfoRouteOpen() {
        given().when().get("/info").then().statusCode(200);
    }

    private static void accept(String path, String body) {
        var request = given().auth().oauth2(TestTokens.admin()).contentType("application/json");
        if (body != null) {
            request = request.body(body);
        }
        request.when().post(path).then().statusCode(202);
    }
}
