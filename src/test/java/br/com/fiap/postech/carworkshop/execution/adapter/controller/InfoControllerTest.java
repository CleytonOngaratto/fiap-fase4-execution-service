package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class InfoControllerTest {

    @Test
    void info_returnsNameAndVersionFromTheBuild() {
        given().when().get("/info").then()
                .statusCode(200)
                .body("name", is("execution-service"))
                .body("version", is("1.0.0-SNAPSHOT"));
    }

    @Test
    void readiness_isUp() {
        given().when().get("/q/health/ready").then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
