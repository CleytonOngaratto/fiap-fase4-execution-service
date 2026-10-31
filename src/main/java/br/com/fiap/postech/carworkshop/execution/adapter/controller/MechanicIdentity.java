package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import org.eclipse.microprofile.jwt.JsonWebToken;

final class MechanicIdentity {

    private MechanicIdentity() {
    }

    // getName(), nunca uma claim fixa: o token real desta plataforma traz so "sub" e o token dos testes traz so
    // "upn", entao ler qualquer uma das duas direto da verde mentindo num lado e vermelho sem motivo no outro.
    // getName() cai em upn, depois preferred_username, depois sub.
    static String of(JsonWebToken jwt) {
        return jwt.getName();
    }
}
