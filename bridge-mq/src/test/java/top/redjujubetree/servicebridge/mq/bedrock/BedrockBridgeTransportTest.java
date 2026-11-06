package top.redjujubetree.servicebridge.mq.bedrock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import top.redjujubetree.bedrock.mq.entity.BedrockSubscription;
import top.redjujubetree.bedrock.mq.mapper.BedrockSubscriptionMapper;
import top.redjujubetree.bedrock.mq.producer.MessageProducer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockBridgeTransportTest {

    @Test
    void sendsSerializedJsonWithoutParsingItAgain() {
        MessageProducer producer = mock(MessageProducer.class);
        BedrockSubscriptionMapper subscriptions = mock(BedrockSubscriptionMapper.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(subscriptions.findEnabledByTopic("bridge.request"))
                .thenReturn(Collections.singletonList(new BedrockSubscription()));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        String payload = "{\"requestId\":\"r-1\",\"data\":{\"value\":42}}";

        new BedrockBridgeAutoConfiguration().bedrockBridgeTransport(
                producer, subscriptions, transactions,
                new MockEnvironment().withProperty("spring.application.name", "bridge-client"))
                .send("bridge.request", "r-1", payload);

        verify(producer).send("bridge.request", "bridge-client", payload);
    }
}
