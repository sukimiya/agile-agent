package io.github.sukimiya.agileagent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

/** Starts one isolated loopback Redis instance for Redis-backed tests in this test JVM. */
public final class LocalRedisTestServer {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final LocalRedisTestServer INSTANCE = start();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::close, "local-redis-test-server-cleanup"));
    }

    private final int port;
    private final Path directory;
    private final Process process;

    private LocalRedisTestServer(int port, Path directory, Process process) {
        this.port = port;
        this.directory = directory;
        this.process = process;
    }

    public static int port() {
        return INSTANCE.port;
    }

    public static RedisStandaloneConfiguration configuration(int database) {
        var configuration = new RedisStandaloneConfiguration(LOOPBACK_HOST, INSTANCE.port);
        configuration.setDatabase(database);
        return configuration;
    }

    private static LocalRedisTestServer start() {
        Path directory = null;
        Process process = null;
        try {
            int port = availablePort();
            directory = Files.createTempDirectory("agile-agent-test-redis-");
            process = new ProcessBuilder("redis-server", "--bind", LOOPBACK_HOST,
                    "--port", Integer.toString(port), "--protected-mode", "yes",
                    "--save", "", "--appendonly", "no", "--dir", directory.toString(),
                    "--loglevel", "warning")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            LocalRedisTestServer server = new LocalRedisTestServer(port, directory, process);
            server.awaitReady();
            return server;
        } catch (Exception failure) {
            stop(process);
            deleteTree(directory);
            if (failure instanceof IllegalStateException stateFailure) throw stateFailure;
            throw new IllegalStateException("Local Redis tests require redis-server on PATH", failure);
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getByName(LOOPBACK_HOST))) {
            return socket.getLocalPort();
        }
    }

    private void awaitReady() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        IOException lastConnectionFailure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Local Redis test process exited before becoming ready");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(LOOPBACK_HOST, port), 100);
                socket.setSoTimeout(500);
                socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                byte[] response = socket.getInputStream().readNBytes(7);
                if ("+PONG\r\n".equals(new String(response, StandardCharsets.US_ASCII))) return;
            } catch (IOException notReady) {
                lastConnectionFailure = notReady;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("Local Redis test process did not become ready", lastConnectionFailure);
    }

    private void close() {
        stop(process);
        deleteTree(directory);
    }

    private static void stop(Process process) {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException cleanupFailure) {
            throw new IllegalStateException("Could not clean up the Local Redis test directory", cleanupFailure);
        }
    }
}
