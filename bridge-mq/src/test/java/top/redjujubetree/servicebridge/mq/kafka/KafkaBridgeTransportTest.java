package top.redjujubetree.servicebridge.mq.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.concurrent.ListenableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaBridgeTransportTest {
    @Test
    @SuppressWarnings("unchecked")
    void waitsForBrokerAcknowledgment() throws Exception {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        ListenableFuture<?> future = mock(ListenableFuture.class);
        when(template.send("requests", "request-1", "{}"))
                .thenReturn((ListenableFuture) future);

        new KafkaBridgeAutoConfiguration().kafkaBridgeTransport(template)
                .send("requests", "request-1", "{}");

        verify(future).get();
    }
}
