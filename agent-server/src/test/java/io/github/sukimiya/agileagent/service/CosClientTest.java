package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.config.CosProperties;

import reactor.core.publisher.Mono;

class CosClientTest {

    private static final String ENDPOINT = "https://example-1234567890.cos.ap-hongkong.myqcloud.com";
    private static final String SECRET_ID = "AKIDtestsecretid0000000000000000";
    private static final String SECRET_KEY = "testsecretkey0000000000000000";
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String KEY = "chat-uploads/conv-1/abc123.png";

    private static CosProperties props() {
        var props = new CosProperties();
        props.setEndpoint(ENDPOINT);
        props.setSecretId(SECRET_ID);
        props.setSecretKey(SECRET_KEY);
        return props;
    }

    @Test
    void putSendsTheIndependentlyDerivedSignatureInAuthorization() {
        var captured = new AtomicReference<org.springframework.web.reactive.function.client.ClientRequest>();
        var builder = WebClient.builder().exchangeFunction(req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        var cos = new CosClient(props(), builder, Clock.fixed(NOW, ZoneOffset.UTC));

        cos.put(KEY, new byte[]{1, 2, 3}, "image/png").block();

        var req = captured.get();
        assertThat(req.url().toString()).isEqualTo(ENDPOINT + "/" + KEY);
        var params = new TreeMap<String, String>();
        for (var pair : req.headers().getFirst("Authorization").split("&")) {
            var kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        var keyTime = (NOW.getEpochSecond() - 60) + ";" + (NOW.getEpochSecond() + 600);
        assertThat(params.get("q-sign-algorithm")).isEqualTo("sha1");
        assertThat(params.get("q-ak")).isEqualTo(SECRET_ID);
        assertThat(params.get("q-sign-time")).isEqualTo(keyTime);
        assertThat(params.get("q-key-time")).isEqualTo(keyTime);
        assertThat(params.get("q-header-list")).isEqualTo("host");
        assertThat(params.get("q-url-param-list")).isEmpty();
        assertThat(params.get("q-signature")).isEqualTo(expectedSignature("put", "/" + KEY, keyTime));
    }

    @Test
    void putFailsWithIoErrorOnNon2xx() {
        var builder = WebClient.builder().exchangeFunction(
                req -> Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN).build()));
        var cos = new CosClient(props(), builder, Clock.fixed(NOW, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> cos.put(KEY, new byte[]{1}, "image/png").block())
                .hasRootCauseInstanceOf(java.io.IOException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining(SECRET_KEY);
    }

    /** Re-derives the COS signature from the algorithm's steps, independently of {@link CosClient}. */
    private static String expectedSignature(String method, String uriPath, String keyTime) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            var signKey = hex(mac.doFinal(keyTime.getBytes(StandardCharsets.UTF_8)));

            var httpString = method + "\n" + uriPath + "\n\n"
                    + "host=" + java.net.URLEncoder.encode("example-1234567890.cos.ap-hongkong.myqcloud.com",
                            StandardCharsets.UTF_8) + "\n";
            var httpStringSha1 = hex(MessageDigest.getInstance("SHA-1")
                    .digest(httpString.getBytes(StandardCharsets.UTF_8)));
            var stringToSign = "sha1\n" + keyTime + "\n" + httpStringSha1 + "\n";

            mac.init(new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return hex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
