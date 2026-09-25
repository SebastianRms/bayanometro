package com.apuestas.ai.rag;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StrategyRagRetrieverTest {

    @Inject
    StrategyRagRetriever ragRetriever;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    StrategyKnowledgeIngestor ingestor;

    @Test
    @DisplayName("Debe recuperar conocimiento matemático del Criterio de Kelly (fórmula f*, Half/Quarter Kelly)")
    void testKellyCriterionRetrieval() {
        String query = "¿Cómo se calcula la fracción de bankroll con el Criterio de Kelly y qué es Half Kelly?";
        var searchResult = searchStore(query, 3, 0.55);

        assertFalse(searchResult.matches().isEmpty(), "Debe recuperar fragmentos para Criterio de Kelly");
        boolean containsKellyFormula = searchResult.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("Kelly") || m.embedded().text().contains("f^*"));
        assertTrue(containsKellyFormula, "Los fragmentos recuperados deben contener la formulación de Kelly");
    }

    @Test
    @DisplayName("Debe recuperar modelado de Poisson y Valor Esperado (EV+)")
    void testExpectedValueAndPoissonRetrieval() {
        String query = "Modelado de goles de fútbol con distribución de Poisson y valor esperado positivo EV";
        var searchResult = searchStore(query, 3, 0.55);

        assertFalse(searchResult.matches().isEmpty(), "Debe recuperar fragmentos para Poisson y EV");
        boolean containsPoissonOrEv = searchResult.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("Poisson") || m.embedded().text().contains("EV"));
        assertTrue(containsPoissonOrEv, "Los fragmentos deben contener referencias a Poisson o EV");
    }

    @Test
    @DisplayName("Debe recuperar fórmulas de Dutching, Hedging y trampa del Vig")
    void testDutchingAndHedgingRetrieval() {
        String query = "Fórmula de reparto de stakes para Dutching multiselección y trampa de la misma casa vig";
        var searchResult = searchStore(query, 3, 0.55);

        assertFalse(searchResult.matches().isEmpty(), "Debe recuperar fragmentos para Dutching y Hedging");
        boolean containsDutching = searchResult.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("Dutching") || m.embedded().text().contains("Hedging"));
        assertTrue(containsDutching, "Los fragmentos deben explicar Dutching o Hedging");
    }

    @Test
    @DisplayName("Debe recuperar Closing Line Value (CLV) y desmargenación fair odds")
    void testClosingLineValueRetrieval() {
        String query = "Cómo calcular el Closing Line Value CLV frente a la cuota de cierre y remover vig";
        var searchResult = searchStore(query, 3, 0.55);

        assertFalse(searchResult.matches().isEmpty(), "Debe recuperar fragmentos para CLV");
        boolean containsClv = searchResult.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("CLV") || m.embedded().text().contains("Closing Line"));
        assertTrue(containsClv, "Los fragmentos deben incluir el análisis de CLV");
    }

    @Test
    @DisplayName("Debe recuperar prueba de ruina de la Martingala y falacias cognitivas")
    void testMartingaleAndFallaciesRetrieval() {
        String query = "Por qué la Martingala lleva a la ruina matemática en apuestas deportivas";
        var searchResult = searchStore(query, 3, 0.55);

        assertFalse(searchResult.matches().isEmpty(), "Debe recuperar fragmentos para Martingala");
        boolean containsMartingale = searchResult.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("Martingala") || m.embedded().text().contains("Falacia"));
        assertTrue(containsMartingale, "Los fragmentos deben incluir el desarme de la Martingala");
    }

    @Test
    @DisplayName("Debe integrar correctamente con RetrievalAugmentor de LangChain4j")
    void testRetrievalAugmentorIntegration() {
        var augmentor = ragRetriever.get();
        assertNotNull(augmentor, "El RetrievalAugmentor no debe ser nulo");

        UserMessage userMessage = UserMessage.from("Quiero apostar con Martingala doblando mi dinero");
        AugmentationRequest request = new AugmentationRequest(
                userMessage,
                Metadata.from(userMessage, null, null)
        );

        AugmentationResult result = augmentor.augment(request);
        assertNotNull(result, "El AugmentationResult no debe ser nulo");
        List<Content> contents = result.contents();
        assertNotNull(contents, "La lista de contenidos aumentados no debe ser nula");
        assertFalse(contents.isEmpty(), "Debe incluir contenidos aumentados de la base de conocimiento");

        boolean mentionsMartingaleOrRisk = contents.stream()
                .anyMatch(c -> c.textSegment().text().contains("Martingala") || c.textSegment().text().contains("Kelly"));
        assertTrue(mentionsMartingaleOrRisk, "El contexto inyectado al LLM debe contener conceptos clave");
    }

    @Test
    @DisplayName("Consulta deportiva de estrategia debe tener similitud significativamente mayor que una no relacionada")
    void testUnrelatedQueryLowRelevance() {
        String strategyQuery = "¿Cómo se calcula la fracción de bankroll con el Criterio de Kelly?";
        String cookingQuery = "Receta para preparar pasta italiana al pesto con albahaca fresca";

        var strategyResult = searchStore(strategyQuery, 1, 0.5);
        var cookingResult = searchStore(cookingQuery, 1, 0.5);

        assertFalse(strategyResult.matches().isEmpty(), "Debe encontrar match para consulta de estrategia");
        assertFalse(cookingResult.matches().isEmpty(), "Debe encontrar match para consulta de cocina");

        double strategyScore = strategyResult.matches().get(0).score();
        double cookingScore = cookingResult.matches().get(0).score();

        assertTrue(strategyScore > cookingScore + 0.10,
                "La consulta estratégica (" + strategyScore + ") debe superar significativamente a la de cocina (" + cookingScore + ")");
    }

    private EmbeddingSearchResult<TextSegment> searchStore(String query, int maxResults, double minScore) {
        var queryEmbedding = embeddingModel.embed(query).content();
        var searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        return embeddingStore.search(searchRequest);
    }
}
