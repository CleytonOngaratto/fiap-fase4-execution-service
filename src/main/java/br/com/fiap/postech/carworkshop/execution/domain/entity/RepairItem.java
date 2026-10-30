package br.com.fiap.postech.carworkshop.execution.domain.entity;

public record RepairItem(String type, Long catalogId, String description, int quantity) {
}
