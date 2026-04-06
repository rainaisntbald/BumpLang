package bump;

public final class EnumValueInstance extends BumpInstance {
    private final String variant;
    private final int ordinal;

    public EnumValueInstance(
            BumpClass runtimeClass,
            String variant,
            int ordinal,
            Object nameValue,
            Object ordinalValue,
            Object attachedValue
    ) {
        super(runtimeClass);
        this.variant = variant;
        this.ordinal = ordinal;
        defineField("name", nameValue);
        defineField("ordinal", ordinalValue);
        defineField("value", attachedValue);
    }

    public String variant() {
        return variant;
    }

    public int ordinal() {
        return ordinal;
    }

    @Override
    public String toString() {
        return runtimeClass.name + "." + variant;
    }
}
