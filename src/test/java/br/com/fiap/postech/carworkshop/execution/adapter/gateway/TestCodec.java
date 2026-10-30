package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;

// O ObjectMapper do codec e injetado num campo de pacote: so uma classe deste pacote consegue monta-lo para um
// teste puro, sem subir o Quarkus.
public final class TestCodec {

    private TestCodec() {
    }

    public static SagaEnvelopeCodec codec() {
        SagaEnvelopeCodec codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
        return codec;
    }
}
