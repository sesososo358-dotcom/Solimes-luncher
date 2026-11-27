package com.solimes.client.core;

import java.util.*;

/** Holds every option, grouped by category (in registration order). */
public final class OptionRegistry {
    private final Map<String, List<Option<?>>> categories = new LinkedHashMap<>();
    private final Map<String, Option<?>> byKey = new LinkedHashMap<>();

    public <O extends Option<?>> O register(O option) {
        if (byKey.containsKey(option.key())) {
            throw new IllegalArgumentException("Duplicate option key: " + option.key());
        }
        byKey.put(option.key(), option);
        categories.computeIfAbsent(option.category(), c -> new ArrayList<>()).add(option);
        return option;
    }

    public Collection<Option<?>> all() { return Collections.unmodifiableCollection(byKey.values()); }
    public Map<String, List<Option<?>>> categories() { return Collections.unmodifiableMap(categories); }
    public Option<?> find(String key) { return byKey.get(key); }

    @SuppressWarnings("unchecked")
    public <T> Option<T> get(String key) {
        Option<?> o = byKey.get(key);
        if (o == null) throw new NoSuchElementException(key);
        return (Option<T>) o;
    }

    public boolean bool(String key) { return (Boolean) get(key).get(); }
    public int integer(String key) { return (Integer) get(key).get(); }
    public String string(String key) { return (String) get(key).get(); }
}
