package com.apuestas.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Proveedor de memoria conversacional acotada con política de expiración.
 * Utiliza Caffeine Cache para prevenir fugas de memoria (Memory Leaks) en el heap.
 */
public class ChatMemoryConfig implements Supplier<ChatMemoryProvider> {

    private static final Cache<Object, ChatMemory> MEMORIES = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build();

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> MEMORIES.get(memoryId, id -> 
            MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(30)
                .build()
        );
    }
}
