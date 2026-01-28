package io.jfrtail.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jfr-tail")
public class JfrTailProperties {
    /**
     * Whether to enable automatic JFR-tailing.
     */
    private boolean enabled = true;

    /**
     * Port for the Web Dashboard.
     */
    private int webPort = 8080;

    /**
     * Port for the TCP Event Stream.
     */
    private int tcpPort = 7099;

    /**
     * Shared secret for authentication.
     */
    private String secret;

    /**
     * Whether to enable the HTML Dashboard endpoint (/jfr/dashboard).
     */
    private boolean dashboardEnabled = true;

    /**
     * Whether to enable the JSON Stats endpoint (/jfr/stats).
     */
    private boolean statsEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isDashboardEnabled() {
        return dashboardEnabled;
    }

    public void setDashboardEnabled(boolean dashboardEnabled) {
        this.dashboardEnabled = dashboardEnabled;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }
}
