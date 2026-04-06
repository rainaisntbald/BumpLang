package ast;

public class EnumVariant {
    public final String name;
    public final String payloadTypeName;
    public final String payloadName;
    public final Expr attachedValue;

    public EnumVariant(String name, String payloadTypeName, String payloadName, Expr attachedValue) {
        this.name = name;
        this.payloadTypeName = payloadTypeName;
        this.payloadName = payloadName;
        this.attachedValue = attachedValue;
    }
}
