package bump;

public interface BumpMethod extends BumpCallable {
    BumpCallable bind(BumpInstance instance);
}
