package scg.fusion.exceptions;

import static java.lang.String.format;

public class  IllegalContractException extends FusionRuntimeException {
    public IllegalContractException(String message, Object...args) {
        super(format(message, args));
    }
}
