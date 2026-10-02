package top.redjujubetree.servicebridge.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "service.bridge.client")
public class ServiceBridgeClientProperties {

    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(60);
    private String redisChannel = "service-bridge:completed";
    private int maxWaitingRequests = 10000;
    private boolean reconciliationEnabled = false;
    private Duration reconciliationInterval = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getRedisChannel() {
        return redisChannel;
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }

    public int getMaxWaitingRequests() {
        return maxWaitingRequests;
    }

    public void setMaxWaitingRequests(int maxWaitingRequests) {
        this.maxWaitingRequests = maxWaitingRequests;
    }

    public boolean isReconciliationEnabled() {
        return reconciliationEnabled;
    }

    public void setReconciliationEnabled(boolean reconciliationEnabled) {
        this.reconciliationEnabled = reconciliationEnabled;
    }

    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }
}
