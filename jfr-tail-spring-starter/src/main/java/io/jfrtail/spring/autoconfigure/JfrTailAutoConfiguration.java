package io.jfrtail.spring.autoconfigure;

import io.jfrtail.agent.api.JfrTailMonitor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@AutoConfiguration
@ConditionalOnProperty(prefix = "jfr-tail", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JfrTailProperties.class)
public class JfrTailAutoConfiguration {

    private final JfrTailProperties properties;

    public JfrTailAutoConfiguration(JfrTailProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getSecret() != null) {
            System.setProperty("jfrtail.secret", properties.getSecret());
        }

        try {
            JfrTailMonitor.getInstance().start(
                    properties.getWebPort(),
                    properties.getTcpPort(),
                    properties.getSecret(),
                    properties.isStatsEnabled(),
                    properties.isDashboardEnabled());
            System.out.println("JFR-Tail Auto-configured: Web Port " + properties.getWebPort() + ", TCP Port "
                    + properties.getTcpPort());
        } catch (Exception e) {
            System.err.println("JFR-Tail Auto-configuration failed: " + e.getMessage());
        }
    }
}
