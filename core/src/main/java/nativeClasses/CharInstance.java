package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class CharInstance extends BumpInstance {
    private final char value;

    public CharInstance(BumpClass runtimeClass, char value) {
        super(runtimeClass);
        this.value = value;
    }

    public char value() {
        return value;
    }

    @Override
    public String toString() {
        return Character.toString(value);
    }
}
