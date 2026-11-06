package top.redjujubetree.servicebridge.client.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.protocol.config.BridgeTopicProperties;
import top.redjujubetree.servicebridge.client.config.ServiceBridgeClientProperties;
import top.redjujubetree.servicebridge.client.core.BridgeCompletionCoordinator;
import top.redjujubetree.servicebridge.client.core.BridgeReconciliationScheduler;
import top.redjujubetree.servicebridge.client.core.BridgeRequestIdGenerator;
import top.redjujubetree.servicebridge.client.core.BridgeResponseFactory;
import top.redjujubetree.servicebridge.client.core.BridgeResultReconciler;
import top.redjujubetree.servicebridge.client.core.DefaultServiceBridgeClient;
import top.redjujubetree.servicebridge.client.core.ServiceBridgeClient;
import top.redjujubetree.servicebridge.client.core.TaskWaitRegistry;
import top.redjujubetree.servicebridge.client.core.UuidBridgeRequestIdGenerator;
import top.redjujubetree.servicebridge.client.redis.BridgeResponseInboundHandler;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import top.redjujubetree.servicebridge.client.redis.RedisCompletionListener;
import top.redjujubetree.servicebridge.client.redis.RedisCompletionPublisher;
import top.redjujubetree.servicebridge.client.store.BridgeRequestStore;
import top.redjujubetree.servicebridge.client.store.jdbc.JdbcBridgeRequestStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@ConditionalOnClass({DeferredResult.class, StringRedisTemplate.class})
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
    @ConditionalOnMissingBean
    public BridgeRequestStore bridgeRequestStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcBridgeRequestStore(jdbc);
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
            BridgeRequestStore store) {
        return new BridgeCompletionCoordinator(registry, store);
    }

    @Bean
    public RedisCompletionPublisher redisCompletionPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ServiceBridgeClientProperties properties) {
        return new RedisCompletionPublisher(redisTemplate, objectMapper, properties);
    }

    @Bean
    public BridgeResponseInboundHandler bridgeResponseInboundHandler(
            ObjectMapper objectMapper,
            RedisCompletionPublisher publisher,
            BridgeTopicProperties topics) {
        return new BridgeResponseInboundHandler(objectMapper, publisher, topics);
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
            BridgeMessagePublisher publisher,
            BridgeTopicProperties topics,
            PlatformTransactionManager transactionManager) {
        return new DefaultServiceBridgeClient(
                properties,
                idGenerator,
                requestStore,
                registry,
                coordinator,
                responseFactory,
                publisher,
                topics,
                new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "service.bridge.client",
            name = "reconciliation-enabled",
            havingValue = "true")
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
            havingValue = "true")
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
            havingValue = "true")
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
