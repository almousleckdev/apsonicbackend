package com.apsonic.backend.modules.navigation.domain;

import java.util.Optional;

public enum MenuKey {
    HEADER(3, 12),
    FOOTER(2, 12);

    /** Levels allowed: 1 = no dropdown, 2 = one dropdown level, ... */
    public final int maxDepth;
    public final int maxTopLevel;

    MenuKey(int maxDepth, int maxTopLevel) {
        this.maxDepth = maxDepth;
        this.maxTopLevel = maxTopLevel;
    }

    public static Optional<MenuKey> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (MenuKey key : values()) {
            if (key.name().equalsIgnoreCase(value)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}
