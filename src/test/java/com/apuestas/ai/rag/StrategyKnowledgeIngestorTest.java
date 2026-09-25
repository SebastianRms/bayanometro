package com.apuestas.ai.rag;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StrategyKnowledgeIngestorTest {

    @Inject
    StrategyKnowledgeIngestor ingestor;

    @Test
    @DisplayName("Todos los archivos Markdown de estrategia matemática deben existir en el classpath")
    void testClasspathKnowledgeFilesExist() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String filePath : StrategyKnowledgeIngestor.KNOWLEDGE_FILES) {
            try (InputStream is = cl.getResourceAsStream(filePath)) {
                assertNotNull(is, "El archivo " + filePath + " debe existir en resources");
                byte[] bytes = is.readAllBytes();
                assertTrue(bytes.length > 500, "El archivo " + filePath + " no debe estar vacío (>500 bytes)");
            } catch (Exception e) {
                fail("Error al leer " + filePath + ": " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("La base de conocimiento en pgvector debe contener los segmentos indexados")
    void testExistingSegmentsCount() {
        int count = ingestor.countExistingSegments();
        assertTrue(count >= 25, "La tabla de embeddings debe contener al menos 25 segmentos indexados, actual: " + count);
    }

    @Test
    @DisplayName("La ingesta idempotente (force=false) no debe duplicar segmentos")
    void testIdempotentIngestion() {
        int initialCount = ingestor.countExistingSegments();
        assertTrue(initialCount >= 25, "Debe haber segmentos existentes");

        int resultCount = ingestor.ingest(false);
        assertEquals(initialCount, resultCount, "La re-ingesta sin forzar debe mantener el conteo intacto sin duplicados");

        int finalCount = ingestor.countExistingSegments();
        assertEquals(initialCount, finalCount, "El conteo en la base de datos no debe cambiar");
    }

    @Test
    @DisplayName("La ingesta forzada (force=true) debe limpiar y re-indexar los fragmentos con hashes consistentes")
    void testForcedReingest() {
        int reingestedCount = ingestor.ingest(true);
        assertTrue(reingestedCount >= 25, "La re-ingesta forzada debe re-indexar al menos 25 fragmentos");

        int storedCount = ingestor.countExistingSegments();
        assertEquals(reingestedCount, storedCount, "El número de registros almacenados debe coincidir exactamente con los reingestados");
    }
}
