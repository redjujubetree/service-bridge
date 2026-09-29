package com.lnsoft.erp.servicebridge.client.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.protocol.BridgeTopicProperties;
import com.lnsoft.erp.servicebridge.client.config.ServiceBridgeClientProperties;
import com.lnsoft.erp.servicebridge.client.core.BridgeCompletionCoordinator;
import com.lnsoft.erp.servicebridge.client.core.BridgeReconciliationScheduler;
import com.lnsoft.erp.servicebridge.client.core.BridgeRequestIdGenerator;
import com.lnsoft.erp.servicebridge.client.core.BridgeResponseFactory;
import com.lnsoft.erp.servicebridge.client.core.BridgeResultReconciler;
import com.lnsoft.erp.servicebridge.client.core.DefaultServiceBridgeClient;
import com.lnsoft.erp.servicebridge.client.core.ServiceBridgeClient;
import com.lnsoft.erp.servicebridge.client.core.TaskWaitRegistry;
import com.lnsoft.erp.servicebridge.client.core.UuidBridgeRequestIdGenerator;
import com.lnsoft.erp.servicebridge.client.redis.BridgeResponseBedrockConsumer;
import com.lnsoft.erp.servicebridge.client.redis.RedisCompletionListener;
import com.lnsoft.erp.servicebridge.client.redis.RedisCompletionPublisher;
import com.lnsoft.erp.servicebridge.client.store.BridgeRequestStore;
import com.lnsoft.erp.servicebridge.client.store.jdbc.JdbcBridgeRequestStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.bedrock.mq.config.BedrockAutoConfiguration;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

@Configuration
@AutoConfigureBefore(BedrockAutoConfiguration.class)
@ConditionalOnClass({DeferredResult.class, MessageProducer.class, StringRedisTemplate.class})
@ConditionalOnProperty(
        prefix = "service.bridge.client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ServiceBridgeClientProperties.class)
public class ServiceBridgeClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "service.bridge")
    public BridgeTopicProperties bridgeTopicProperties() {
        return new BridgeTopicProperties();
    }

    @Bean
    public static BridgeClientConsumerRegistrar bridgeClientConsumerRegistrar(
            ApplicationContext applicationContext) {
        return new BridgeClientConsumerRegistrar(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public BridgeRequestStore bridgeRequestStore(NamedParameterJdbcTemplate jdbc,
                                                 ObjectMapper objectMapper) {
        return new JdbcBridgeRequestStore(jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BridgeRequestIdGenerator bridgeRequestIdGenerator() {
        return new UuidBridgeRequestIdGenerator();
    }

    @Bean
    public TaskWaitRegistry taskWaitRegistry(ServiceBridgeClientProperties properties) {
        return new TaskWaitRegistry(properties.getMaxWaitingRequests());
    }

    @Bean
    @ConditionalOnMissingBean
    public BridgeResponseFactory bridgeResponseFactory(ObjectMapper objectMapper) {
        return new BridgeResponseFactory(objectMapper);
    }

    @Bean
    public BridgeCompletionCoordinator bridgeCompletionCoordinator(
            TaskWaitRegistry registry,
            BridgeRequestStore store,
            BridgeResponseFactory responseFactory) {
        return new BridgeCompletionCoordinator(registry, store, responseFactory);
    }

    @Bean
    public RedisCompletionPublisher redisCompletionPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ServiceBridgeClientProperties properties) {
        return new RedisCompletionPublisher(redisTemplate, objectMapper, properties);
    }

    @Bean
    public BridgeResponseBedrockConsumer bridgeResponseBedrockConsumer(
            ObjectMapper objectMapper,
            RedisCompletionPublisher publisher) {
        return new BridgeResponseBedrockConsumer(objectMapper, publisher);
    }

    @Bean
    public RedisCompletionListener redisCompletionListener(
            ObjectMapper objectMapper,
            BridgeCompletionCoordinator coordinator) {
        return new RedisCompletionListener(objectMapper, coordinator);
    }

    @Bean(name = "serviceBridgeRedisExecutor")
    public ThreadPoolTaskExecutor serviceBridgeRedisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("service-bridge-redis-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "serviceBridgeRedisListenerContainer")
    public RedisMessageListenerContainer serviceBridgeRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisCompletionListener listener,
            ServiceBridgeClientProperties properties,
            @Qualifier("serviceBridgeRedisExecutor") ThreadPoolTaskExecutor serviceBridgeRedisExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(serviceBridgeRedisExecutor);
        container.addMessageListener(listener, new ChannelTopic(properties.getRedisChannel()));
        return container;
    }

    @Bean
    public ServiceBridgeClient serviceBridgeClient(
            ServiceBridgeClientProperties properties,
            BridgeRequestIdGenerator idGenerator,
            BridgeRequestStore requestStore,
            TaskWaitRegistry registry,
            BridgeCompletionCoordinator coordinator,
            BridgeResponseFactory responseFactory,
            MessageProducer producer,
            BridgeTopicProperties topics,
            PlatformTransactionManager transactionManager) {
        return new DefaultServiceBridgeClient(
                properties,
                idGenerator,
                requestStore,
                registry,
                coordinator,
                responseFactory,
                producer,
                topics,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.client",
            name = "reconciliation-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeResultReconciler bridgeResultReconciler(
            TaskWaitRegistry registry,
            BridgeRequestStore store,
            BridgeCompletionCoordinator coordinator) {
        return new BridgeResultReconciler(registry, store, coordinator);
    }

    @Bean(name = "serviceBridgeReconciliationTaskScheduler")
    @ConditionalOnProperty(
            prefix = "service.bridge.client",
            name = "reconciliation-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ThreadPoolTaskScheduler serviceBridgeReconciliationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("service-bridge-reconcile-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.client",
            name = "reconciliation-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BridgeReconciliationScheduler bridgeReconciliationScheduler(
            @Qualifier("serviceBridgeReconciliationTaskScheduler")
            ThreadPoolTaskScheduler serviceBridgeReconciliationTaskScheduler,
            BridgeResultReconciler reconciler,
            ServiceBridgeClientProperties properties) {
        return new BridgeReconciliationScheduler(
                serviceBridgeReconciliationTaskScheduler,
                reconciler,
                properties.getReconciliationInterval());
    }
}
