package com.lnsoft.erp.servicebridge.server.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.server.config.ServiceBridgeServerProperties;
import com.lnsoft.erp.servicebridge.server.consumer.BridgeRequestBedrockConsumer;
import com.lnsoft.erp.servicebridge.server.core.BridgeProcessingRecovery;
import com.lnsoft.erp.servicebridge.server.core.BridgeProcessingRecoveryScheduler;
import com.lnsoft.erp.servicebridge.server.core.BridgeRequestExecutor;
import com.lnsoft.erp.servicebridge.server.core.BridgeRequestHandler;
import com.lnsoft.erp.servicebridge.server.core.BridgeRequestHandlerRegistry;
import com.lnsoft.erp.servicebridge.server.core.BridgeResultCommitter;
import com.lnsoft.erp.servicebridge.server.store.BridgeResultStore;
import com.lnsoft.erp.servicebridge.server.store.jdbc.JdbcBridgeResultStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import top.redjujubetree.bedrock.mq.config.BedrockAutoConfiguration;
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
            ServiceBridgeServerProperties properties) {
        return new BridgeResultCommitter(resultStore, messageProducer, properties);
    }

    @Bean
    public BridgeRequestExecutor bridgeRequestExecutor(
            BridgeRequestHandlerRegistry registry,
            BridgeResultStore resultStore,
            BridgeResultCommitter committer,
            ServiceBridgeServerProperties properties,
            ObjectMapper objectMapper) {
        return new BridgeRequestExecutor(registry, resultStore, committer, properties, objectMapper);
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
            ServiceBridgeServerProperties properties) {
        return new BridgeProcessingRecovery(resultStore, committer, properties);
    }

    @Bean(name = "serviceBridgeServerRecoveryTaskScheduler")
    @ConditionalOnProperty(
            prefix = "service.bridge.server",
            name = "recovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ThreadPoolTaskScheduler serviceBridgeServerRecoveryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("service-bridge-recovery-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.server",
            name = "recovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeProcessingRecoveryScheduler bridgeProcessingRecoveryScheduler(
            @Qualifier("serviceBridgeServerRecoveryTaskScheduler") ThreadPoolTaskScheduler scheduler,
            BridgeProcessingRecovery recovery,
            ServiceBridgeServerProperties properties) {
        return new BridgeProcessingRecoveryScheduler(
                scheduler, recovery, properties.getRecoveryInterval());
    }
}
