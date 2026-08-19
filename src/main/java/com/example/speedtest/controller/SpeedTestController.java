package com.example.speedtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class SpeedTestController {

    // =========================
    // RATE LIMITING
    // =========================
    // Tracks how many download/upload streams each IP currently has open.
    // The frontend opens up to 4 streams per test, so we cap a bit above
    // that to allow for retries/overlap without allowing unlimited abuse.
    private static final int MAX_CONCURRENT_PER_IP = 8;

    // Hard ceiling on how long any single stream is allowed to run, no
    // matter what the client does. This is the most important safety net —
    // it guarantees no connection can silently run forever and rack up
    // unbounded bandwidth usage.
    private static final long MAX_STREAM_DURATION_MS = 15_000;

    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    private String getClientIp(HttpServletRequest request) {
        // Render (and most hosts/proxies) put the real client IP here;
        // falls back to the direct connection IP if the header is absent.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean tryAcquireSlot(String ip) {
        AtomicInteger count = activeConnections.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int updated = count.incrementAndGet();
        if (updated > MAX_CONCURRENT_PER_IP) {
            count.decrementAndGet(); // undo — we're rejecting this one
            return false;
        }
        return true;
    }

    private void releaseSlot(String ip) {
        AtomicInteger count = activeConnections.get(ip);
        if (count != null) {
            count.decrementAndGet();
        }
    }
    // =========================
    // PING
    // =========================

    @GetMapping("/api/speed/ping")
    public String ping() {
        // Deliberately does nothing but respond — any real work here would
        // skew the round-trip time the frontend is trying to measure.
        return "pong";
    }

    // =========================
    // DOWNLOAD
    // =========================

    @GetMapping("/api/speed/download")
    public void downloadTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String ip = getClientIp(request);

        if (!tryAcquireSlot(ip)) {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("Too many concurrent connections. Please wait and try again.");
            return;
        }

        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");

            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[1024 * 1024]; // 1 MB

            long startTime = System.nanoTime();

            try {
                // Streams continuously — no fixed byte cap — so the client
                // never has to reconnect mid-test. The only two things that
                // stop it are the client disconnecting (normal case) or the
                // hard time cap below (safety net).
                while ((System.nanoTime() - startTime) / 1_000_000 < MAX_STREAM_DURATION_MS) {
                    outputStream.write(buffer);
                    outputStream.flush();
                }
            } catch (IOException e) {
                // Client aborted — this is the expected way a test ends.
            }
        } finally {
            releaseSlot(ip);
        }
    }

    // =========================
    // UPLOAD
    // =========================

    @PostMapping("/api/speed/upload")
    public void uploadTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String ip = getClientIp(request);

        if (!tryAcquireSlot(ip)) {
            response.setStatus(429);
            response.getWriter().write("Too many concurrent connections. Please wait and try again.");
            return;
        }
        try {
            byte[] buffer = new byte[64 * 1024];
            InputStream in = request.getInputStream();
            long startTime = System.nanoTime();
            // Reads and discards the body as fast as it arrives — we only
            // care about how quickly the bytes got here, not their content.
            // Bounded by the same hard time cap as download, so a client
            // that keeps sending data can't hold the connection forever.
            while ((System.nanoTime() - startTime) / 1_000_000 < MAX_STREAM_DURATION_MS) {
                int bytesRead = in.read(buffer);
                if (bytesRead == -1) break; // client finished sending
            }
            response.setStatus(200);
            response.getWriter().write("OK");
        } finally {
            releaseSlot(ip);
        }
    }
}