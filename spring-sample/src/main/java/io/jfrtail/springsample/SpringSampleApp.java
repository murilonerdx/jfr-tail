package io.jfrtail.springsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.util.Random;

@SpringBootApplication
@RestController
public class SpringSampleApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringSampleApp.class, args);
    }

    @GetMapping("/hello")
    public String hello() throws InterruptedException {
        Thread.sleep(new Random().nextInt(500)); // Latency
        return "Hello World";
    }

    @GetMapping("/error-test")
    public String error() {
        throw new RuntimeException("Test Exception from Spring");
    }

    @GetMapping("/heavy")
    public String heavy() {
        byte[] data = new byte[10 * 1024 * 1024]; // 10MB Alloc to trigger GC
        return "Allocated " + data.length;
    }
}
