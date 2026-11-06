package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import org.springframework.http.ResponseEntity;

/** Maps the stored outcome and client-side failures to an application's HTTP response. */
public interface BridgeResponseHandler<T> {

    ResponseEntity<T> fromStored(BridgeStoredResult stored);

    ResponseEntity<T> timeout(String requestId);

    ResponseEntity<T> unavailable(String requestId, String message);
}
