package com.lnsoft.glxx.servicebridge.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.glxx.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.glxx.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.glxx.servicebridge.server.consumer.BridgeRequestBedrockConsumer;
import com.lnsoft.glxx.servicebridge.server.consumer.BridgeRecoveryBedrockConsumer;
import com.lnsoft.glxx.servicebridge.server.core.BridgeProcessingRecovery;
import com.lnsoft.glxx.servicebridge.server.core.BridgeRequestAcquirer;
import com.lnsoft.glxx.servicebridge.server.core.BridgeRequestExecutor;
import com.lnsoft.glxx.servicebridge.server.core.BridgeRequestHandler;
import com.lnsoft.glxx.servicebridge.server.core.BridgeRequestHandlerRegistry;
import com.lnsoft.glxx.servicebridge.server.core.BridgeResultCommitter;
import com.lnsoft.glxx.servicebridge.server.store.BridgeResultStore;
import com.lnsoft.glxx.servicebridge.server.store.jdbc.JdbcBridgeResultStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import top.redjujubetree.bedrock.mq.config.BedrockAutoConfiguration;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@AutoConfigureBefore(BedrockAutoConfiguration.class)
@ConditionalOnClass(MessageProducer.class)
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
    public static BridgeServerConsumerRegistrar bridgeServerConsumerRegistrar(
            ApplicationContext applicationContext) {
        return new BridgeServerConsumerRegistrar(applicationContext);
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
            MessageProducer messageProducer,
            BridgeTopicProperties topics,
            ServiceBridgeServerProperties properties) {
        return new BridgeResultCommitter(resultStore, messageProducer, topics, properties);
    }

    @Bean
    public BridgeRequestAcquirer bridgeRequestAcquirer(
            BridgeResultStore resultStore,
            MessageProducer messageProducer,
            BedrockSubscriptionMapper subscriptionMapper,
            ServiceBridgeServerProperties properties) {
        return new BridgeRequestAcquirer(resultStore, messageProducer,
                subscriptionMapper, properties);
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
    public BridgeRequestBedrockConsumer bridgeRequestBedrockConsumer(
            ObjectMapper objectMapper,
            BridgeRequestExecutor executor) {
        return new BridgeRequestBedrockConsumer(objectMapper, executor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.server",
            name = "recovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeProcessingRecovery bridgeProcessingRecovery(
            BridgeResultStore resultStore,
            BridgeResultCommitter committer,
            MessageProducer messageProducer,
            ServiceBridgeServerProperties properties) {
        return new BridgeProcessingRecovery(resultStore, committer, messageProducer, properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.server",
            name = "recovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeRecoveryBedrockConsumer bridgeRecoveryBedrockConsumer(
            ObjectMapper objectMapper, BridgeProcessingRecovery recovery) {
        return new BridgeRecoveryBedrockConsumer(objectMapper, recovery);
    }
}
