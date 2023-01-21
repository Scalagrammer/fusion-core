package scg.fusion.exceptions;

public class PointcutExpressionSyntaxError extends FusionRuntimeException {

    public final String expression;
    public final String message;
    public final int position;

    public PointcutExpressionSyntaxError(String expression, String message, int position) {
        super(message);
        this.expression = expression;
        this.message = message;
        this.position = position;
    }
}
