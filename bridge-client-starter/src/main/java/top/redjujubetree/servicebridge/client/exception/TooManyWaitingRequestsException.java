package top.redjujubetree.servicebridge.client.exception;

public class TooManyWaitingRequestsException extends RuntimeException {

    public TooManyWaitingRequestsException(String message) {
        super(message);
    }
}
