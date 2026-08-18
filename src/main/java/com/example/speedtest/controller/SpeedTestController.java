package com.example.speedtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RestController
public class SpeedTestController {

    // =========================
    // PING
    // =========================
    @GetMapping("/api/speed/ping")
    public String ping() {
        return "pong";
    }

    // =========================
    // DOWNLOAD
    // =========================

    @GetMapping("/api/speed/download")
    public void downloadTest(HttpServletResponse response) throws IOException {
        response.setContentType("application/octet-stream");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        OutputStream outputStream = response.getOutputStream();
        byte[] buffer = new byte[1024 * 1024]; // 1 MB

        try {
            // No fixed cap — keep writing until the client disconnects
            // (testDownload() in the frontend calls controller.abort() itself)
            while (true) {
                outputStream.write(buffer);
                outputStream.flush(); // push each chunk immediately, don't let it batch up
            }
        } catch (IOException e) {
            // Client aborted — this is the expected way the stream ends
        }
    }

    // =========================
    // UPLOAD
    // =========================
    @PostMapping("/api/speed/upload")
    public void uploadTest(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        InputStream in = request.getInputStream();
        while (in.read(buffer) != -1) {
            // just discard — we only care about how fast it arrived
        }
    }
}