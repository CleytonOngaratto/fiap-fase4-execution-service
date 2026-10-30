package br.com.fiap.postech.carworkshop.execution.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosedItemTest {

    @Test
    void tellsAPartFromAService() {
        assertTrue(new DiagnosedItem(DiagnosedItem.PART, 12L, 1).isPart());
        assertFalse(new DiagnosedItem(DiagnosedItem.SERVICE, 7L, 1).isPart());
    }

    @Test
    void acceptsOnlyAnItemTheCatalogCanAnswerFor() {
        assertTrue(new DiagnosedItem(DiagnosedItem.PART, 12L, 3).isWellFormed());
        assertTrue(new DiagnosedItem(DiagnosedItem.SERVICE, 7L, 1).isWellFormed());
        assertFalse(new DiagnosedItem("WHATEVER", 7L, 1).isWellFormed());
        assertFalse(new DiagnosedItem(null, 7L, 1).isWellFormed());
        assertFalse(new DiagnosedItem(DiagnosedItem.PART, null, 1).isWellFormed());
        assertFalse(new DiagnosedItem(DiagnosedItem.PART, 0L, 1).isWellFormed());
        assertFalse(new DiagnosedItem(DiagnosedItem.PART, 12L, 0).isWellFormed());
    }
}
