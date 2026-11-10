package br.com.nize.domain;

import java.util.List;

/** The Nize palette for categories and people (REQ-052, D-21). Stored as the key; the front maps it to colors. */
public final class TagColor {

    public static final List<String> KEYS = List.of("green", "teal", "blue", "purple", "pink", "red", "orange", "brown");

    /** For {@code @Pattern}: one of the keys. */
    public static final String PATTERN = "green|teal|blue|purple|pink|red|orange|brown";

    private TagColor() {
    }
}
