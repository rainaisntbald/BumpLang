package bump;

import java.util.List;

public final class EnumVariantConstructor implements BumpCallable {
    private final BumpClass enumClass;
    private final String variant;
    private final int ordinal;
    private final String payloadName;

    public EnumVariantConstructor(BumpClass enumClass, String variant, int ordinal, String payloadName) {
        this.enumClass = enumClass;
        this.variant = variant;
        this.ordinal = ordinal;
        this.payloadName = payloadName;
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object payload = arguments.get(0);
        EnumValueInstance value = new EnumValueInstance(
                enumClass,
                variant,
                ordinal,
                interpreter.wrapString(variant),
                interpreter.wrapInteger(ordinal),
                payload
        );
        value.defineField(payloadName, payload);
        return value;
    }

    @Override
    public String toString() {
        return "<enum-variant " + enumClass.name + "." + variant + ">";
    }
}
