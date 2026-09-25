package com.apuestas.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/**
 * Proveedor de aumentación RAG para el asistente analítico de Bayanómetro.
 * Recupera contexto estratégico y matemático (Kelly, EV+, Poisson, Dutching, Hedging, CLV, Falacias)
 * almacenado vectorialmente en pgvector.
 */
@ApplicationScoped
public class StrategyRagRetriever implements Supplier<RetrievalAugmentor> {

    private final RetrievalAugmentor augmentor;
    private final EmbeddingStoreContentRetriever contentRetriever;

    @Inject
    public StrategyRagRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            @ConfigProperty(name = "bayanometro.rag.max-results", defaultValue = "3") int maxResults,
            @ConfigProperty(name = "bayanometro.rag.min-score", defaultValue = "0.55") double minScore) {

        this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        this.augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return augmentor;
    }

    public EmbeddingStoreContentRetriever getContentRetriever() {
        return contentRetriever;
    }
}
