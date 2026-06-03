package io.github.atomoty.faultpilot.server;

import io.github.atomoty.faultpilot.server.config.FaultPilotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FaultPilotProperties.class)
public class FaultPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaultPilotApplication.class, args);
    }
}
