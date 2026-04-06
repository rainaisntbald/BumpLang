package bump;

import java.util.ArrayList;
import java.util.List;

record ParsedTypeName(String name, List<ParsedTypeName> typeArguments) {
    static ParsedTypeName parse(String input) {
        Cursor cursor = new Cursor(input);
        ParsedTypeName parsed = parseType(cursor);
        if (!cursor.atEnd()) {
            throw BumpException.semantic("Invalid type syntax '" + input + "'.");
        }
        return parsed;
    }

    private static ParsedTypeName parseType(Cursor cursor) {
        String name = cursor.readIdentifier();
        if (name == null) {
            throw BumpException.semantic("Invalid type syntax.");
        }
        if (!cursor.match('<')) {
            return new ParsedTypeName(name, List.of());
        }
        List<ParsedTypeName> arguments = new ArrayList<>();
        do {
            arguments.add(parseType(cursor));
        } while (cursor.match(','));
        if (!cursor.match('>')) {
            throw BumpException.semantic("Invalid type syntax.");
        }
        return new ParsedTypeName(name, List.copyOf(arguments));
    }

    private static final class Cursor {
        private final String text;
        private int index = 0;

        Cursor(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        boolean match(char expected) {
            if (atEnd() || text.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }

        String readIdentifier() {
            if (atEnd()) {
                return null;
            }
            int start = index;
            char first = text.charAt(index);
            if (!Character.isLetter(first) && first != '_') {
                return null;
            }
            index++;
            while (!atEnd()) {
                char c = text.charAt(index);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    index++;
                } else {
                    break;
                }
            }
            return text.substring(start, index);
        }
    }
}
