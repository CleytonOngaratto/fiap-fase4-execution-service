package br.com.fiap.postech.carworkshop.execution.domain.entity;

public record DiagnosedItem(String type, Long catalogId, int quantity) {

    public static final String SERVICE = "SERVICE";
    public static final String PART = "PART";

    public boolean isPart() {
        return PART.equals(type);
    }

    public boolean isWellFormed() {
        return (SERVICE.equals(type) || PART.equals(type)) && catalogId != null && catalogId > 0 && quantity > 0;
    }
}
