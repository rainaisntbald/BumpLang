package nativeClasses;

import bump.BumpClass;
import bump.BumpException;
import bump.BumpInstance;
import bump.BumpRuntimeError;

import java.nio.file.Path;

public class FileInstance extends BumpInstance {
    private final Path path;
    private boolean open;

    public FileInstance(BumpClass runtimeClass, Path path) {
        super(runtimeClass);
        this.path = path;
        this.open = false;
    }

    public Path path() {
        return path;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if(!path.toFile().exists()) {
            throw new BumpRuntimeError(BumpRuntimeError.ErrorType.GENERIC, BumpException.runtime("File does not exist: " + path));
        }
        open = true;
    }

    public void close() {
        open = false;
    }

    @Override
    public String toString() {
        return "<file " + path + (open ? " open>" : " closed>");
    }
}
