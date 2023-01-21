package scg.fusion.exceptions;

public abstract class FusionRuntimeException extends RuntimeException {

    protected FusionRuntimeException() {
        super();
    }

    protected FusionRuntimeException(Throwable cause) {
        super(cause);
    }

    protected FusionRuntimeException(String message) {
        super(message);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

}
