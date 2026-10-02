package top.redjujubetree.servicebridge.mq.rocketmq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketBridgeTransportTest {
    @Test
    void rejectsUnconfirmedSend() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(producer.send(any(Message.class))).thenReturn(result);

        assertThrows(IllegalStateException.class, () ->
                new RocketBridgeAutoConfiguration().rocketBridgeTransport(producer)
                        .send("requests", "request-1", "{}"));
    }
}
