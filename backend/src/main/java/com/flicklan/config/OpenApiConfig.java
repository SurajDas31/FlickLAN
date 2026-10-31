package com.flicklan.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlickLAN Local Chat REST & WebSocket API")
                        .version(appVersion)
                        .description("""
                                RESTful endpoints and real-time WebSocket protocol specifications for FlickLAN:
                                - **Chat & History**: Public rooms, 1-to-1 DMs, message logs.
                                - **LAN Peer Discovery**: Query host, active mesh peers, and mobile clients.
                                - **File Transfers**: High-speed chunked LAN file uploads & downloads.
                                - **Backup & Restore**: Snapshot export and idempotent restore (JSON/ZIP).
                                - **Auto-Update**: Query new versions and trigger atomic restarts.
                                """)
                        .contact(new Contact().name("FlickLAN Project Team"))
                        .license(new License().name("MIT License")))
                .servers(List.of(
                        new Server().url("/").description("Current FlickLAN Instance")
                ));
    }
}

