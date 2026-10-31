package br.com.fiap.postech.carworkshop.execution.support;

import io.smallrye.jwt.build.Jwt;

import java.util.Set;

// Assina igual ao JwtTokenAdapter do OS Service: subject, issuer e groups, sem upn. O token de teste tem de ter
// a mesma forma do real, senao uma leitura de identidade passa aqui e volta nula em producao.
public final class TestTokens {

    public static final String ISSUER = "https://oficina-api.com";

    private TestTokens() {
    }

    public static String admin() {
        return signedFor(ExecutionFixtures.MECHANIC, "ADMIN");
    }

    public static String customer() {
        return signedFor("cliente.pedro", "CUSTOMER");
    }

    private static String signedFor(String username, String role) {
        return Jwt.claims()
                .subject(username)
                .issuer(ISSUER)
                .groups(Set.of(role))
                .expiresIn(3600)
                .sign();
    }
}
