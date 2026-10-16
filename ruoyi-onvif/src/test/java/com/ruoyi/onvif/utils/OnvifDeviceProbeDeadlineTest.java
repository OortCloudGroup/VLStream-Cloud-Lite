package com.ruoyi.onvif.utils;

import com.ruoyi.onvif.domain.bo.FetchMainAndSubStreamUrisBo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

/** Actual loopback HTTP sockets, including cancellation of body reads; no real cameras touched. */
public class OnvifDeviceProbeDeadlineTest {
    private static final byte[] INFORMATION = ("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Body>"
        + "<GetDeviceInformationResponse xmlns='" + OnvifDeviceProbe.DEVICE + "'><Manufacturer>fixture</Manufacturer>"
        + "</GetDeviceInformationResponse></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);

    @BeforeClass public static void warmHttpClientAndVerifyNormalResponse() throws Exception {
        try (Fixture server = new Fixture(exchange -> reply(exchange, 200, INFORMATION))) {
            assertEquals("AVAILABLE", new OnvifDeviceProbe(3000).checkManagement(server.account()));
        }
    }

    @Test(timeout = 5000) public void dripFeedCannotExtendTheTotalDeadlineAndSocketIsClosed() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        try (Fixture server = new Fixture(exchange -> drip(exchange, disconnected))) {
            long started = System.nanoTime();
            assertEquals("UNREACHABLE", new OnvifDeviceProbe(500).checkManagement(server.account()));
            long elapsed = elapsed(started);
            assertTrue("500ms total deadline took " + elapsed + "ms", elapsed < 1300);
            assertTrue("Peer must observe abort, not a detached running request", disconnected.await(1500, TimeUnit.MILLISECONDS));
            System.out.println("ONVIF drip-feed total elapsedMillis=" + elapsed + ", peerClosed=true");
        }
    }

    @Test(timeout = 5000) public void authenticationFallbackSharesOneBudgetIncludingHttpDigestRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean digestSeen = new AtomicBoolean();
        try (Fixture server = new Fixture(exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt <= 2) {
                if (attempt == 1) Thread.sleep(650);
                exchange.getResponseHeaders().set("WWW-Authenticate", "Digest realm=\"fixture\", nonce=\"deadline\", qop=\"auth\"");
                exchange.sendResponseHeaders(401, -1);
            } else {
                digestSeen.set(exchange.getRequestHeaders().getFirst("Authorization").startsWith("Digest "));
                Thread.sleep(500);
                reply(exchange, 200, INFORMATION);
            }
        })) {
            long started = System.nanoTime();
            assertEquals("UNREACHABLE", new OnvifDeviceProbe(1000).checkManagement(server.account()));
            long elapsed = elapsed(started);
            assertEquals(3, requests.get());
            assertTrue(digestSeen.get());
            assertTrue("Fallback must not get another 1000ms", elapsed < 1500);
            System.out.println("ONVIF WS/Digest shared total elapsedMillis=" + elapsed + ", requests=" + requests.get());
        }
    }

    @Test(timeout = 5000) public void declaredOversizeIsRejectedWithoutWaitingForTheBody() throws Exception {
        try (Fixture server = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 65537);
            exchange.getResponseBody().flush();
            Thread.sleep(2000);
        })) {
            long started = System.nanoTime();
            assertEquals("MANAGEMENT_UNAVAILABLE", new OnvifDeviceProbe(3000).checkManagement(server.account()));
            assertTrue("Declared size must be checked before reading", elapsed(started) < 1000);
        }
    }

    @Test(timeout = 5000) public void chunkedOversizeIsBoundedWithoutContentLength() throws Exception {
        try (Fixture server = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            byte[] payload = largeInformation();
            for (int offset = 0; offset < payload.length; offset += 8192) {
                exchange.getResponseBody().write(payload, offset, Math.min(8192, payload.length - offset));
                exchange.getResponseBody().flush();
            }
        })) {
            assertEquals("MANAGEMENT_UNAVAILABLE", new OnvifDeviceProbe(3000).checkManagement(server.account()));
        }
    }

    @Test(timeout = 5000) public void compressedResponseIsLimitedAfterDecompression() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(largeInformation()); }
        assertTrue(bytes.size() < 65536);
        try (Fixture server = new Fixture(exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            reply(exchange, 200, bytes.toByteArray());
        })) {
            assertEquals("MANAGEMENT_UNAVAILABLE", new OnvifDeviceProbe(3000).checkManagement(server.account()));
        }
    }

    @Test(timeout = 7000) public void bothProbeWorkersReturnAndCanCheckNormalDevicesAfterAbort() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch disconnected = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (Fixture server = new Fixture(exchange -> {
            if (requests.incrementAndGet() <= 2) drip(exchange, disconnected);
            else reply(exchange, 200, INFORMATION);
        })) {
            Future<String> first = workers.submit(() -> new OnvifDeviceProbe(500).checkManagement(server.account()));
            Future<String> second = workers.submit(() -> new OnvifDeviceProbe(500).checkManagement(server.account()));
            assertEquals("UNREACHABLE", first.get(2, TimeUnit.SECONDS));
            assertEquals("UNREACHABLE", second.get(2, TimeUnit.SECONDS));
            assertTrue(disconnected.await(1500, TimeUnit.MILLISECONDS));
            Future<String> recoveredFirst = workers.submit(() -> new OnvifDeviceProbe(1000).checkManagement(server.account()));
            Future<String> recoveredSecond = workers.submit(() -> new OnvifDeviceProbe(1000).checkManagement(server.account()));
            assertEquals("AVAILABLE", recoveredFirst.get(2, TimeUnit.SECONDS));
            assertEquals("AVAILABLE", recoveredSecond.get(2, TimeUnit.SECONDS));
            assertEquals(4, requests.get());
        } finally {
            workers.shutdownNow();
            assertTrue("No abandoned HTTP probe workers", workers.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private static byte[] largeInformation() {
        char[] text = new char[131072];
        java.util.Arrays.fill(text, 'x');
        // Valid SOAP: without the size cap this would incorrectly become AVAILABLE.
        return new String(INFORMATION, StandardCharsets.UTF_8).replace("fixture", new String(text)).getBytes(StandardCharsets.UTF_8);
    }
    private static void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/soap+xml; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
    private static void drip(HttpExchange exchange, CountDownLatch disconnected) throws Exception {
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream output = exchange.getResponseBody();
            // Complete valid SOAP would take several seconds; each chunk is below the idle timeout.
            for (int offset = 0; offset < INFORMATION.length; offset += 4) {
                output.write(INFORMATION, offset, Math.min(4, INFORMATION.length - offset));
                output.flush();
                Thread.sleep(80);
            }
        } catch (IOException peerClosed) { disconnected.countDown(); }
    }
    private interface Handler { void handle(HttpExchange exchange) throws Exception; }
    private static class Fixture implements AutoCloseable {
        final HttpServer server;
        final ExecutorService peers = Executors.newCachedThreadPool();
        Fixture(Handler handler) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(peers);
            server.createContext("/onvif/device_service", exchange -> {
                try {
                    byte[] input = new byte[1024];
                    while (exchange.getRequestBody().read(input) >= 0) { /* Drain only the test request body. */ }
                    handler.handle(exchange);
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (IOException closed) { /* Expected client abort for oversize/deadline cases. */ }
                catch (Exception error) { throw new RuntimeException(error); }
                finally { exchange.close(); }
            });
            server.start();
        }
        FetchMainAndSubStreamUrisBo account() {
            FetchMainAndSubStreamUrisBo bo = new FetchMainAndSubStreamUrisBo();
            bo.setIp("127.0.0.1:" + server.getAddress().getPort());
            bo.setUsername("fixture"); bo.setPassword("fixture-only");
            return bo;
        }
        public void close() { server.stop(0); peers.shutdownNow(); }
    }
}
