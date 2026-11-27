package com.solimes.client.core;

/** Base class for every configurable setting shown in the Solimes Mod Menu. */
public abstract class Option<T> {
    private final String category;
    private final String key;
    private final String label;
    private final T defaultValue;
    private T value;

    protected Option(String category, String key, String label, T defaultValue) {
        this.category = category;
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String category() { return category; }
    public String key() { return key; }
    public String label() { return label; }
    public T defaultValue() { return defaultValue; }
    public T get() { return value; }
    public void set(T v) { this.value = sanitize(v); }
    public void reset() { this.value = defaultValue; }

    /** Clamp / validate an incoming value. */
    protected T sanitize(T v) { return v; }

    public abstract String serialize();

    /** Must never throw: invalid text leaves the current value unchanged. */
    public abstract void deserialize(String text);
}
