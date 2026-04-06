package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

import java.util.ArrayList;
import java.util.List;

public class ArrayInstance extends BumpInstance {
    private final List<Object> values;

    public ArrayInstance(BumpClass runtimeClass, List<Object> values) {
        super(runtimeClass);
        this.values = new ArrayList<>(values);
    }

    public List<Object> values() {
        return values;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
