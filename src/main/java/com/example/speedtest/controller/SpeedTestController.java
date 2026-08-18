package com.example.speedtest.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
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

        // 1 MB buffer
        byte[] buffer = new byte[1024 * 1024];

        // Send 100 MB
        int totalMB = 100;

        try {

            for (int i = 0; i < totalMB; i++) {

                outputStream.write(buffer);

            }

            outputStream.flush();

        } catch (IOException e) {

            // Browser stops the request when the test finishes.
            System.out.println("Download connection stopped.");

        } finally {

            outputStream.close();
        }
    }

    // =========================
    // UPLOAD
    // =========================
    @PostMapping("/api/speed/upload")
    public String uploadTest(@RequestBody byte[] data) {

        return "OK";
    }
}