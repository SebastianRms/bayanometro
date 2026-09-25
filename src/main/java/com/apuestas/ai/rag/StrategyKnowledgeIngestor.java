package com.apuestas.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Servicio encargado de inicializar, validar y sembrar la base de conocimientos estratégicos cuantitativos
 * en pgvector al arrancar la aplicación de manera idempotente, con autodetección de cambios por hash.
 */
@ApplicationScoped
public class StrategyKnowledgeIngestor {

    private static final Logger LOG = Logger.getLogger(StrategyKnowledgeIngestor.class);

    public static final List<String> KNOWLEDGE_FILES = List.of(
            "knowledge/kelly_criterion.md",
            "knowledge/expected_value_poisson.md",
            "knowledge/dutching_hedging.md",
            "knowledge/closing_line_value_clv.md",
            "knowledge/gamblers_fallacies.md"
    );

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "quarkus.langchain4j.pgvector.table", defaultValue = "strategy_embeddings")
    String tableName;

    @ConfigProperty(name = "bayanometro.rag.force-reingest", defaultValue = "false")
    boolean forceReingest;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Verificando estado de la base de conocimiento vectorial en tabla: " + tableName);
        try {
            ingest(forceReingest);
        } catch (Exception e) {
            LOG.error("Error durante la inicialización de la base de conocimiento: " + e.getMessage(), e);
        }
    }

    /**
     * Sincroniza e ingesta los documentos de conocimiento en pgvector.
     * Si force es true o se detectan inconsistencias/cambios de hash, se purga y re-ingesta la base.
     *
     * @param force si es true, fuerza la regeneración y reindexación completa.
     * @return número de fragmentos indexados.
     */
    public synchronized int ingest(boolean force) {
        List<KnowledgeFileDoc> fileDocs = loadKnowledgeFiles();
        if (fileDocs.isEmpty()) {
            LOG.warn("No se encontraron documentos de conocimiento para ingerir.");
            return 0;
        }

        String combinedHash = computeCombinedHash(fileDocs);

        if (!force && isUpToDate(combinedHash, fileDocs.size())) {
            LOG.info("Base de conocimiento estratégico ya está sincronizada y al día en pgvector (" + combinedHash.substring(0, 8) + "). Omitiendo reingesta.");
            return countExistingSegments();
        }

        LOG.info("Iniciando ingesta (" + (force ? "forzada" : "nueva/actualizada") + ") de estrategia cuantitativa a pgvector (hash: " + combinedHash.substring(0, 8) + ")...");

        // Limpiar registros antiguos para evitar duplicación
        try {
            embeddingStore.removeAll();
        } catch (Exception e) {
            LOG.debug("Nota al limpiar embeddings existentes: " + e.getMessage());
        }

        var splitter = DocumentSplitters.recursive(650, 70);
        List<TextSegment> allSegments = new ArrayList<>();
        for (KnowledgeFileDoc kfd : fileDocs) {
            Document doc = Document.from(kfd.content(), Metadata.from("file_name", kfd.filePath()));
            List<TextSegment> segments = splitter.split(doc);
            for (TextSegment seg : segments) {
                Metadata meta = Metadata.from("file_name", kfd.filePath())
                        .put("hash", kfd.fileHash())
                        .put("combined_hash", combinedHash);
                allSegments.add(TextSegment.from(seg.text(), meta));
            }
        }

        LOG.info("Generando " + allSegments.size() + " embeddings in-process para la base de conocimiento...");
        List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();

        LOG.info("Persistiendo " + allSegments.size() + " embeddings y segmentos en pgvector (tabla: " + tableName + ")...");
        embeddingStore.addAll(embeddings, allSegments);

        LOG.info("Ingesta de estrategia cuantitativa completada exitosamente en pgvector (" + allSegments.size() + " fragmentos indexados).");
        return allSegments.size();
    }

    private List<KnowledgeFileDoc> loadKnowledgeFiles() {
        List<KnowledgeFileDoc> list = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = StrategyKnowledgeIngestor.class.getClassLoader();
        }

        for (String filePath : KNOWLEDGE_FILES) {
            try (InputStream is = cl.getResourceAsStream(filePath)) {
                if (is == null) {
                    LOG.warn("No se encontró el archivo de conocimiento en classpath: " + filePath);
                    continue;
                }
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String hash = sha256(content);
                list.add(new KnowledgeFileDoc(filePath, content, hash));
                LOG.info("Documento cargado: " + filePath + " (" + content.length() + " caracteres, hash: " + hash.substring(0, 8) + ")");
            } catch (Exception e) {
                LOG.error("Error leyendo archivo " + filePath + ": " + e.getMessage(), e);
            }
        }
        return list;
    }

    private boolean isUpToDate(String expectedCombinedHash, int expectedFileCount) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT count(*), count(DISTINCT metadata->>'file_name') FROM " + tableName +
                         " WHERE metadata->>'combined_hash' = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, expectedCombinedHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long totalSegments = rs.getLong(1);
                        long distinctFiles = rs.getLong(2);
                        LOG.info("Estado actual en " + tableName + ": " + totalSegments + " segmentos válidos para " + distinctFiles + " archivos.");
                        return totalSegments >= 25 && distinctFiles == expectedFileCount;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("La tabla " + tableName + " aún no existe o no tiene registros válidos: " + e.getMessage());
        }
        return false;
    }

    public int countExistingSegments() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            LOG.tracef(e, "Error al consultar conteo de segmentos existentes");
        }
        return 0;
    }

    private String computeCombinedHash(List<KnowledgeFileDoc> docs) {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeFileDoc doc : docs) {
            sb.append(doc.filePath()).append(":").append(doc.fileHash()).append(";");
        }
        return sha256(sb.toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    public record KnowledgeFileDoc(String filePath, String content, String fileHash) {}
}
