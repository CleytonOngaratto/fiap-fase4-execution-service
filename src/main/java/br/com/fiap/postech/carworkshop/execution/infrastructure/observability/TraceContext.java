package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import java.util.Map;

public interface TraceContext {

    Map<String, String> capture();

    void accept(Map<String, String> headers);

    void nameTransaction(String category, String name);

    void addAttribute(String key, String value);

    void ignoreTransaction();

    void noticeError(Throwable error);
}
