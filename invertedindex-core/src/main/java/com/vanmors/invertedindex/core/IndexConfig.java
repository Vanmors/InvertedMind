package com.vanmors.invertedindex.core;

import java.nio.file.Path;

public record IndexConfig(
        Path indexDirectory,
        int skipInterval
) {

    public IndexConfig {
        if (indexDirectory == null) throw new IllegalArgumentException("indexDirectory must not be null");
    }

    public static IndexConfig defaults(Path dir) {
        return new IndexConfig(dir, 0);
    }

    public static Builder builder(Path dir) {
        return new Builder(dir);
    }

    public static final class Builder {
        private final Path indexDirectory;
        private int skipInterval = 0; // 0 = auto (sqrt(df))

        private Builder(Path dir) {
            this.indexDirectory = dir;
        }

        public Builder skipInterval(int interval) {
            this.skipInterval = interval;
            return this;
        }

        public IndexConfig build() {
            return new IndexConfig(indexDirectory, skipInterval);
        }
    }
}
