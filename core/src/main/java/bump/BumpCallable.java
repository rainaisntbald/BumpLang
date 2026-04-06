package bump;

import java.util.List;

public interface BumpCallable {
    int arity();
    default boolean acceptsArity(int argumentCount) {
        return arity() == argumentCount;
    }
    Object call(Interpreter interpreter, List<Object> arguments);
}
