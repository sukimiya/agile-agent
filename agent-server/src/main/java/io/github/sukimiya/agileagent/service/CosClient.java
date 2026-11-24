package io.github.sukimiya.agileagent.service;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.config.CosProperties;

import reactor.core.publisher.Mono;

/**
 * Minimal Tencent Cloud COS XML-API client: a signed {@code PUT} for uploads. Reads are plain object URLs —
 * the bucket is public-read by design; only writes are signed. Signing follows "请求签名"
 * (https://cloud.tencent.com/document/product/436/7778): only the
 * {@code host} header is signed, no URL parameters. Error and log messages never carry the signature or the
 * response body (either may echo credentials).
 */
@Service
public class CosClient {

    private static final Duration PUT_SIGN_TTL = Duration.ofMinutes(10);

    private final CosProperties props;
    private final WebClient.Builder webBuilder;
    private final Clock clock;
    private final String host;

    @org.springframework.beans.factory.annotation.Autowired
    public CosClient(CosProperties props, WebClient.Builder webBuilder) {
        this(props, webBuilder, Clock.systemUTC());
    }

    public CosClient(CosProperties props, WebClient.Builder webBuilder, Clock clock) {
        this.props = props;
        this.webBuilder = webBuilder;
        this.clock = clock;
        this.host = props.getEndpoint() == null ? null : URI.create(props.getEndpoint()).getHost();
    }

    /** Uploads {@code body} to {@code key}; a non-2xx response fails with an {@link UncheckedIOException}. */
    public Mono<Void> put(String key, byte[] body, String contentType) {
        String md5;
        try {
            md5 = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("MD5").digest(body));
        } catch (java.security.NoSuchAlgorithmException e) {
            return Mono.error(e);
        }
        return webBuilder.build().put()
                .uri(props.getEndpoint() + "/" + key)
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType))
                .contentLength(body.length)
                .header("Content-MD5", md5)
                .header("Authorization", authorization("put", key, PUT_SIGN_TTL))
                .bodyValue(body)
                .exchangeToMono(resp -> resp.statusCode().is2xxSuccessful()
                        ? resp.releaseBody()
                        : resp.releaseBody().then(Mono.error(new UncheckedIOException(
                                new java.io.IOException("COS PUT failed with status "
                                        + resp.statusCode().value())))));
    }

    private String authorization(String method, String key, Duration ttl) {
        var keyTime = keyTime(ttl);
        return "q-sign-algorithm=sha1"
                + "&q-ak=" + props.getSecretId()
                + "&q-sign-time=" + keyTime
                + "&q-key-time=" + keyTime
                + "&q-header-list=host"
                + "&q-url-param-list="
                + "&q-signature=" + signature(method, key, keyTime);
    }

    private String keyTime(Duration ttl) {
        long now = clock.instant().getEpochSecond();
        return (now - 60) + ";" + (now + ttl.getSeconds());
    }

    private String signature(String method, String key, String keyTime) {
        var signKey = hex(hmacSha1(props.getSecretKey().getBytes(StandardCharsets.UTF_8), keyTime));
        var httpString = method + "\n/" + key + "\n\n" + "host=" + enc(host) + "\n";
        var stringToSign = "sha1\n" + keyTime + "\n" + hex(sha1(httpString)) + "\n";
        return hex(hmacSha1(signKey.getBytes(StandardCharsets.UTF_8), stringToSign));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] sha1(String s) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha1(byte[] key, String message) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
