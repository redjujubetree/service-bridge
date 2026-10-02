package top.redjujubetree.servicebridge.server.core;

import top.redjujubetree.servicebridge.protocol.BridgeRequestMessage;
import top.redjujubetree.servicebridge.protocol.BridgeTopicProperties;
import top.redjujubetree.servicebridge.server.store.BridgeResultStore;
import top.redjujubetree.servicebridge.mq.BridgeMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeResultCommitterTest {

    private BridgeResultStore store;
    private BridgeMessagePublisher publisher;
    private BridgeResultCommitter committer;
    private BridgeTopicProperties topics;

    @BeforeEach
    void setUp() {
        store = mock(BridgeResultStore.class);
        publisher = mock(BridgeMessagePublisher.class);
        topics = new BridgeTopicProperties();
        topics.setResponseTopic("custom.response");
        committer = new BridgeResultCommitter(
                store, publisher, topics);
    }

    @Test
    void completedRequestNotifiesWaitingClient() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);

        committer.completeSuccess(request(), "token", "{}");

        verify(publisher).send(eq("custom.response"), eq("request-1"), any());
    }

    @Test
    void completedRequestDoesNotNotifyTimedOutClient() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);

        committer.completeSuccess(request(), "token", "{}");

        verify(publisher, never()).send(eq("custom.response"), any(), any());
    }

    @Test
    void recoveryFailureDoesNotNotifyTimedOutClient() {
        when(store.completeExpiredFailure(eq("request-1"), eq("token"), eq("PROCESSING_TIMEOUT"),
                any(), any(LocalDateTime.class))).thenReturn(true);

        committer.completeExpiredFailure("request-1", "TEST", "token");

        verify(publisher, never()).send(eq("custom.response"), any(), any());
    }

    @Test
    void recoveryFailureNotifiesWaitingClient() {
        when(store.completeExpiredFailure(eq("request-1"), eq("token"), eq("PROCESSING_TIMEOUT"),
                any(), any(LocalDateTime.class))).thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);

        committer.completeExpiredFailure("request-1", "TEST", "token");

        verify(publisher).send(eq("custom.response"), eq("request-1"), any());
    }

    @Test
    void responseIsSentOnlyAfterResultTransactionCommits() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:response-after-commit");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transaction.execute(status -> {
            committer.completeSuccess(request(), "token", "{}");
            verify(publisher, never()).send(any(), any(), any());
            return null;
        });

        verify(publisher).send(eq("custom.response"), eq("request-1"), any());
    }

    @Test
    void responseSendFailureDoesNotChangeCommittedBusinessResult() {
        when(store.completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class)))
                .thenReturn(true);
        when(store.isClientWaiting(eq("request-1"), any(LocalDateTime.class))).thenReturn(true);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).send(eq("custom.response"), eq("request-1"), any());

        org.junit.jupiter.api.Assertions.assertTrue(committer.completeSuccess(request(), "token", "{}"));
        verify(store).completeSuccess(eq("request-1"), eq("token"), eq("{}"), any(LocalDateTime.class));
    }

    private BridgeRequestMessage request() {
        BridgeRequestMessage request = new BridgeRequestMessage();
        request.setRequestId("request-1");
        request.setBusinessType("TEST");
        return request;
    }
}
