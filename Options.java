package com.solimes.client.core;

/** Concrete option types. Add a new type here and the menu/config pick it up automatically. */
public final class Options {
    private Options() {}

    public static final class Bool extends Option<Boolean> {
        public Bool(String cat, String key, String label, boolean def) { super(cat, key, label, def); }
        @Override public String serialize() { return Boolean.toString(get()); }
        @Override public void deserialize(String t) {
            if (t == null) return;
            String s = t.trim();
            if (s.equalsIgnoreCase("true")) set(true);
            else if (s.equalsIgnoreCase("false")) set(false);
        }
    }

    public static final class Int extends Option<Integer> {
        public final int min, max;
        public Int(String cat, String key, String label, int def, int min, int max) {
            super(cat, key, label, def);
            this.min = min; this.max = max;
            set(def);
        }
        @Override protected Integer sanitize(Integer v) { return Math.max(min, Math.min(max, v)); }
        @Override public String serialize() { return Integer.toString(get()); }
        @Override public void deserialize(String t) {
            try { set(Integer.parseInt(t.trim())); } catch (RuntimeException ignored) { }
        }
    }

    /** ARGB color stored as #AARRGGBB. */
    public static final class Color extends Option<Integer> {
        public Color(String cat, String key, String label, int argb) { super(cat, key, label, argb); }
        @Override public String serialize() { return String.format("#%08X", get()); }
        @Override public void deserialize(String t) {
            try {
                String s = t.trim();
                if (s.startsWith("#")) s = s.substring(1);
                set((int) Long.parseLong(s, 16));
            } catch (RuntimeException ignored) { }
        }
    }

    public static final class Choice extends Option<String> {
        private final String[] choices;
        public Choice(String cat, String key, String label, String def, String... choices) {
            super(cat, key, label, def);
            this.choices = choices.clone();
        }
        public String[] choices() { return choices.clone(); }
        @Override protected String sanitize(String v) {
            for (String c : choices) if (c.equals(v)) return v;
            return defaultValue();
        }
        @Override public String serialize() { return get(); }
        @Override public void deserialize(String t) { if (t != null) set(t.trim()); }
    }

    public static final class Text extends Option<String> {
        public Text(String cat, String key, String label, String def) { super(cat, key, label, def); }
        @Override protected String sanitize(String v) { return v == null ? "" : v; }
        @Override public String serialize() { return get(); }
        @Override public void deserialize(String t) { if (t != null) set(t); }
    }
}
