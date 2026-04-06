package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

import java.util.ArrayList;
import java.util.List;

public class StringInstance extends BumpInstance {
    private final List<Character> chars;

    public StringInstance(BumpClass runtimeClass, String value) {
        super(runtimeClass);
        this.chars = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            chars.add(value.charAt(i));
        }
    }

    public String value() {
        StringBuilder builder = new StringBuilder(chars.size());
        for (char value : chars) {
            builder.append(value);
        }
        return builder.toString();
    }

    public int length() {
        return chars.size();
    }

    public char charAt(int index) {
        return chars.get(index);
    }

    public List<Character> chars() {
        return chars;
    }

    @Override
    public String toString() {
        return value();
    }
}
