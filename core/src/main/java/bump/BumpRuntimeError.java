package bump;

public final class BumpRuntimeError extends RuntimeException {
    public enum ErrorType {
        GENERIC(Builtins.EXCEPTION),
        TYPE(Builtins.TYPE_ERROR),
        NAME(Builtins.NAME_ERROR),
        PROPERTY(Builtins.PROPERTY_ERROR),
        INDEX(Builtins.INDEX_ERROR),
        VALUE(Builtins.VALUE_ERROR),
        ARITY(Builtins.ARITY_ERROR),
        DIVISION_BY_ZERO(Builtins.DIVISION_BY_ZERO_ERROR);

        private final String builtinClassName;

        ErrorType(String builtinClassName) {
            this.builtinClassName = builtinClassName;
        }

        public String builtinClassName() {
            return builtinClassName;
        }
    }

    private final ErrorType type;
    private final BumpException diagnostic;

    public BumpRuntimeError(ErrorType type, BumpException diagnostic) {
        super(null, null, false, false);
        this.type = type;
        this.diagnostic = diagnostic;
    }

    public static BumpRuntimeError error(String message) {
        return new BumpRuntimeError(ErrorType.GENERIC, BumpException.runtime(message));
    }

    public static BumpRuntimeError error(Token token, String message) {
        return new BumpRuntimeError(ErrorType.GENERIC, BumpException.runtime(token, message));
    }

    public static BumpRuntimeError error(int line, String message) {
        return new BumpRuntimeError(ErrorType.GENERIC, BumpException.runtime(line, message));
    }

    public static BumpRuntimeError typeError(String message) {
        return new BumpRuntimeError(ErrorType.TYPE, BumpException.runtime(message));
    }

    public static BumpRuntimeError nameError(String message) {
        return new BumpRuntimeError(ErrorType.NAME, BumpException.runtime(message));
    }

    public static BumpRuntimeError propertyError(String message) {
        return new BumpRuntimeError(ErrorType.PROPERTY, BumpException.runtime(message));
    }

    public static BumpRuntimeError indexError(String message) {
        return new BumpRuntimeError(ErrorType.INDEX, BumpException.runtime(message));
    }

    public static BumpRuntimeError valueError(String message) {
        return new BumpRuntimeError(ErrorType.VALUE, BumpException.runtime(message));
    }

    public static BumpRuntimeError arityError(String message) {
        return new BumpRuntimeError(ErrorType.ARITY, BumpException.runtime(message));
    }

    public static BumpRuntimeError divisionByZero(String message) {
        return new BumpRuntimeError(ErrorType.DIVISION_BY_ZERO, BumpException.runtime(message));
    }

    public BumpException diagnostic() {
        return diagnostic;
    }

    public ErrorType type() {
        return type;
    }

    public BumpRuntimeError withFallbackLocation(int line, int column, int length, String context) {
        BumpException resolved = diagnostic.withFallbackLocation(line, column, length, context);
        if (resolved == diagnostic) {
            return this;
        }
        return new BumpRuntimeError(type, resolved);
    }
}
