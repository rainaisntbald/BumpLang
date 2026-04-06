package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

import java.util.ArrayList;
import java.util.List;

public class MapInstance extends BumpInstance {
    private final List<Object> keys;
    private final List<Object> values;

    public MapInstance(BumpClass runtimeClass) {
        super(runtimeClass);
        this.keys = new ArrayList<>();
        this.values = new ArrayList<>();
    }

    public MapInstance(BumpClass runtimeClass, List<Object> keys, List<Object> values) {
        super(runtimeClass);
        this.keys = new ArrayList<>(keys);
        this.values = new ArrayList<>(values);
    }

    public List<Object> keys() {
        return keys;
    }

    public List<Object> values() {
        return values;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(keys.get(i)).append(": ").append(values.get(i));
        }
        return builder.append("}").toString();
    }
}
