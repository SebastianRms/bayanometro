package com.apuestas.resource;

import com.apuestas.ai.BettingAiAssistant;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
class TelegramWebhookResourceTest {

    private static final String SECRET_TOKEN = "dummy-secret";
    private static final Long ALLOWED_USER = 123456L;
    private static final AtomicLong UPDATE_ID_GEN = new AtomicLong(System.currentTimeMillis());

    @InjectMock
    BettingAiAssistant aiAssistant;

    @Test
    @DisplayName("Debe rechazar solicitudes con token secreto inválido retornando 401")
    void testRechazoTokenInvalido() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", "token-invalido")
                .body(Map.of("update_id", UPDATE_ID_GEN.incrementAndGet()))
                .when()
                .post("/webhook/telegram")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Debe rechazar usuarios no autorizados lanzando excepción correspondiente")
    void testUsuarioNoAutorizado() {
        long unauthorizedUserId = 999999999L;
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET_TOKEN)
                .body(Map.of(
                        "update_id", UPDATE_ID_GEN.incrementAndGet(),
                        "message", Map.of(
                                "message_id", 1,
                                "from", Map.of("id", unauthorizedUserId),
                                "chat", Map.of("id", unauthorizedUserId),
                                "text", "Hola"
                        )
                ))
                .when()
                .post("/webhook/telegram")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Debe procesar mensaje de usuario autorizado e invocar al asistente de IA con RAG")
    void testMensajeUsuarioAutorizado() {
        Mockito.when(aiAssistant.chat(eq(ALLOWED_USER), anyString()))
                .thenReturn("Respuesta estratégica cuantitativa de prueba");

        long updateId = UPDATE_ID_GEN.incrementAndGet();

        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET_TOKEN)
                .body(Map.of(
                        "update_id", updateId,
                        "message", Map.of(
                                "message_id", 2,
                                "from", Map.of("id", ALLOWED_USER),
                                "chat", Map.of("id", ALLOWED_USER),
                                "text", "¿Me conviene apostar con Kelly o Martingala?"
                        )
                ))
                .when()
                .post("/webhook/telegram")
                .then()
                .statusCode(200)
                .body("method", equalTo("sendMessage"))
                .body("chat_id", equalTo(ALLOWED_USER.intValue()))
                .body("text", equalTo("Respuesta estratégica cuantitativa de prueba"));
    }

    @Test
    @DisplayName("Debe manejar idempotencia: no reprocesar el mismo update_id dos veces")
    void testIdempotenciaUpdateId() {
        Mockito.when(aiAssistant.chat(eq(ALLOWED_USER), anyString()))
                .thenReturn("Primera respuesta");

        long updateId = UPDATE_ID_GEN.incrementAndGet();

        // Primer envío -> 200 con respuesta
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET_TOKEN)
                .body(Map.of(
                        "update_id", updateId,
                        "message", Map.of(
                                "message_id", 3,
                                "from", Map.of("id", ALLOWED_USER),
                                "chat", Map.of("id", ALLOWED_USER),
                                "text", "Mensaje único"
                        )
                ))
                .when()
                .post("/webhook/telegram")
                .then()
                .statusCode(200);

        // Segundo envío con mismo update_id -> debe responder 200 vacío sin volver a invocar la IA
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET_TOKEN)
                .body(Map.of(
                        "update_id", updateId,
                        "message", Map.of(
                                "message_id", 3,
                                "from", Map.of("id", ALLOWED_USER),
                                "chat", Map.of("id", ALLOWED_USER),
                                "text", "Mensaje repetido"
                        )
                ))
                .when()
                .post("/webhook/telegram")
                .then()
                .statusCode(200)
                .body(equalTo(""));
    }
}
