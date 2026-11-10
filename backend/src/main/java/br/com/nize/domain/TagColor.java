package br.com.nize.domain;

import java.util.List;

/** Colors of categories and people (REQ-052, D-21): a quick key (mapped to theme tokens by the front) or #rrggbb. */
public final class TagColor {

    public static final List<String> KEYS = List.of("green", "teal", "blue", "purple", "pink", "red", "orange", "brown");

    /** For {@code @Pattern}: one of the quick keys, or any color as lowercase {@code #rrggbb} (REQ-052, 2026-11-09). */
    public static final String PATTERN = "green|teal|blue|purple|pink|red|orange|brown|#[0-9a-f]{6}";

    private TagColor() {
    }
}
