package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class ChatMemory {

    private static final String KEY_PREFIX = "chatmemory:";
    private static final TypeReference<List<LlmClient.Message>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    public ChatMemory(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<List<LlmClient.Message>> get(String ownerId) {
        return redis.opsForValue().get(KEY_PREFIX + ownerId)
                .map(raw -> parse(ownerId, raw))
                .defaultIfEmpty(List.of());
    }

    public Mono<Void> append(String ownerId, List<LlmClient.Message> newMessages) {
        return get(ownerId)
                .map(existing -> {
                    var merged = new ArrayList<>(existing);
                    merged.addAll(newMessages);
                    return serialize(ownerId, merged);
                })
                .flatMap(payload -> redis.opsForValue().set(KEY_PREFIX + ownerId, payload))
                .then();
    }

    private List<LlmClient.Message> parse(String ownerId, String raw) {
        try {
            return json.readValue(raw, MESSAGE_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt chat memory JSON for owner " + ownerId, e);
        }
    }

    private String serialize(String ownerId, List<LlmClient.Message> messages) {
        try {
            return json.writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat memory for owner " + ownerId, e);
        }
    }
}
