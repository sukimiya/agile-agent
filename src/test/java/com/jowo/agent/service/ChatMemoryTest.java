package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class ChatMemoryTest {

    private static final Set<String> OWNER_IDS_USED = Set.of("nobody", "conv-1", "conv-2");

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private ChatMemory memory;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        memory = new ChatMemory(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        for (String ownerId : OWNER_IDS_USED) {
            redisTemplate.delete("chatmemory:" + ownerId).block();
        }
        connectionFactory.destroy();
    }

    @Test
    void getOnUnknownOwnerReturnsEmptyList() {
        assertThat(memory.get("nobody").block()).isEmpty();
    }

    @Test
    void appendThenGetReturnsAppendedMessagesInOrder() {
        memory.append("conv-1", List.of(LlmClient.Message.user("hi"))).block();
        memory.append("conv-1", List.of(LlmClient.Message.assistantText("hello"))).block();

        assertThat(memory.get("conv-1").block()).extracting(LlmClient.Message::content)
                .containsExactly("hi", "hello");
    }

    @Test
    void appendDoesNotCapMessageCount() {
        for (int i = 0; i < 25; i++) {
            memory.append("conv-1", List.of(LlmClient.Message.user("msg-" + i))).block();
        }

        var history = memory.get("conv-1").block();

        assertThat(history).hasSize(25);
        assertThat(history.get(0).content()).isEqualTo("msg-0");
        assertThat(history.get(24).content()).isEqualTo("msg-24");
    }

    @Test
    void ownersAreIsolatedFromEachOther() {
        memory.append("conv-1", List.of(LlmClient.Message.user("from conv-1"))).block();
        memory.append("conv-2", List.of(LlmClient.Message.user("from conv-2"))).block();

        assertThat(memory.get("conv-1").block()).extracting(LlmClient.Message::content).containsExactly("from conv-1");
        assertThat(memory.get("conv-2").block()).extracting(LlmClient.Message::content).containsExactly("from conv-2");
    }
}
