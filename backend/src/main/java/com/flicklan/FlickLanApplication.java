package com.flicklan;

import com.flicklan.service.LanDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.*;
import java.net.URI;

@SpringBootApplication
public class FlickLanApplication {
    private static final Logger logger = LoggerFactory.getLogger(FlickLanApplication.class);

    public static void main(String[] args) {
        // Support running both headless and GUI desktop launch
        System.setProperty("java.awt.headless", "false");

        ConfigurableApplicationContext context = SpringApplication.run(FlickLanApplication.class, args);
        LanDiscoveryService discoveryService = context.getBean(LanDiscoveryService.class);

        int port = discoveryService.getServerPort();
        String localIp = discoveryService.getLocalLanIp();
        String localUrl = "http://localhost:" + port;
        String lanUrl = "http://" + localIp + ":" + port;

        logger.info("=================================================");
        logger.info(" FlickLAN Local Chat is running!");
        logger.info(" Local URL:   {}", localUrl);
        logger.info(" LAN URL:     {}", lanUrl);
        logger.info(" Share LAN URL with anyone on your local Wi-Fi!");
        logger.info("=================================================");

        // Automatically launch the default web browser on desktop startup
        launchBrowser(localUrl);
    }

    private static void launchBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                // Windows runtime fallback
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            }
        } catch (Exception e) {
            logger.info("Could not auto-open browser: {}. Please visit {}", e.getMessage(), url);
        }
    }
}

