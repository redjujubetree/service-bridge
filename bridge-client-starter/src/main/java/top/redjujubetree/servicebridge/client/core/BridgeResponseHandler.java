package top.redjujubetree.servicebridge.client.core;

import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import org.springframework.http.ResponseEntity;

/**
 * Converts a completed request or a client-side failure into an HTTP response.
 * Implementations choose both the HTTP status and the response body. Each method
 * must return a non-null response. A handler shared across requests must be thread-safe.
 *
 * @param <T> response body type
 * @see DefaultBridgeResponseHandler
 */
public interface BridgeResponseHandler<T> {

    /**
     * Builds a response from a stored final result, whether successful or failed.
     *
     * @param stored the final result loaded from the request store
     * @return the HTTP response
     */
    ResponseEntity<T> fromStored(BridgeStoredResult stored);

    /**
     * Builds a response when the HTTP wait expires without a final result.
     *
     * @param requestId ID of the timed-out request
     * @return the HTTP response
     */
    ResponseEntity<T> timeout(String requestId);

    /**
     * Builds a response when the client cannot wait for or submit the request,
     * such as when wait capacity is exhausted or persistence or MQ sending fails.
     *
     * @param requestId ID of the affected request
     * @param message description of the client-side failure
     * @return the HTTP response
     */
    ResponseEntity<T> unavailable(String requestId, String message);
}
