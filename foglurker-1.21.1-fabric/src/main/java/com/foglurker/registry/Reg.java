package com.foglurker.registry;

import java.util.function.Supplier;

/** Something registered at start-up, read with get() (so the code reads the same as with Forge's RegistryObject). */
public final class Reg<T> implements Supplier<T> {
    private final T value;

    private Reg(T value) {
        this.value = value;
    }

    public static <T> Reg<T> of(T value) {
        return new Reg<>(value);
    }

    @Override
    public T get() {
        return value;
    }

    public boolean isPresent() {
        return true;
    }
}
