package top.redjujubetree.servicebridge.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.protocol.BridgeTopicProperties;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.server.config.ServiceBridgeServerProperties;
import top.redjujubetree.servicebridge.server.consumer.BridgeRequestInboundHandler;
import top.redjujubetree.servicebridge.server.core.BridgeProcessingRecovery;
import top.redjujubetree.servicebridge.server.core.BridgeRequestAcquirer;
import top.redjujubetree.servicebridge.server.core.BridgeRequestExecutor;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandlerRegistry;
import top.redjujubetree.servicebridge.server.core.BridgeResultCommitter;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import top.redjujubetree.servicebridge.server.store.jdbc.JdbcBridgeResultStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(
        prefix = "service.bridge.server",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ServiceBridgeServerProperties.class)
public class ServiceBridgeServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "service.bridge")
    public BridgeTopicProperties bridgeTopicProperties() {
        return new BridgeTopicProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public BridgeResultStore bridgeResultStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcBridgeResultStore(jdbc);
    }

    @Bean
    public BridgeRequestHandlerRegistry bridgeRequestHandlerRegistry(
            ObjectProvider<BridgeRequestHandler> handlers) {
        List<BridgeRequestHandler> values = handlers.orderedStream().collect(Collectors.toList());
        return new BridgeRequestHandlerRegistry(values);
    }

    @Bean
    public BridgeResultCommitter bridgeResultCommitter(
            BridgeResultStore resultStore,
            BridgeMessagePublisher publisher,
            BridgeTopicProperties topics) {
        return new BridgeResultCommitter(resultStore, publisher, topics);
    }

    @Bean
    public BridgeRequestAcquirer bridgeRequestAcquirer(
            BridgeResultStore resultStore,
            ServiceBridgeServerProperties properties) {
        return new BridgeRequestAcquirer(resultStore, properties);
    }

    @Bean
    public BridgeRequestExecutor bridgeRequestExecutor(
            BridgeRequestHandlerRegistry registry,
            BridgeRequestAcquirer requestAcquirer,
            BridgeResultCommitter committer,
            ServiceBridgeServerProperties properties,
            ObjectMapper objectMapper) {
        return new BridgeRequestExecutor(registry, requestAcquirer, committer, properties, objectMapper);
    }

    @Bean
    public BridgeRequestInboundHandler bridgeRequestInboundHandler(
            ObjectMapper objectMapper,
            BridgeRequestExecutor executor,
            BridgeTopicProperties topics) {
        return new BridgeRequestInboundHandler(objectMapper, executor, topics);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.server",
            name = "recovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeProcessingRecovery bridgeProcessingRecovery(
            BridgeResultStore resultStore,
            BridgeResultCommitter committer) {
        return new BridgeProcessingRecovery(resultStore, committer);
    }
}
