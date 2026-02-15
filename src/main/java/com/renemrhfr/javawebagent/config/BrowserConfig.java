package com.renemrhfr.javawebagent.config;

/**
 * Immutable configuration for a browser automation session.
 *
 * <p>Controls headless mode, timeouts, resource blocking, snapshot behaviour,
 * and low-resource optimisations. Use {@link Builder} for convenient
 * construction.</p>
 *
 * @param headless                  whether to run the browser in headless mode
 * @param timeoutMs                 default page-navigation timeout in milliseconds
 * @param lowResourceMode           enables aggressive resource-saving optimisations
 * @param blockImages               block image requests to reduce bandwidth
 * @param blockFonts                block font requests to reduce bandwidth
 * @param blockMedia                block media requests to reduce bandwidth
 * @param snapshotMaxChars          maximum character length for accessibility snapshots
 * @param snapshotIncludeDetails    include extended element details in snapshots
 * @param snapshotFilterVisibleOnly only include visible elements in snapshots
 * @param executablePath            optional path to browser executable (null to use default)
 *
 */
public record BrowserConfig(boolean headless, int timeoutMs, boolean lowResourceMode, boolean blockImages,
                            boolean blockFonts, boolean blockMedia, int snapshotMaxChars,
                            boolean snapshotIncludeDetails, boolean snapshotFilterVisibleOnly,
                            String executablePath) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean headless = true;
        private int timeoutMs = 30_000;
        private boolean lowResourceMode = true;
        private boolean blockImages = true;
        private boolean blockFonts = true;
        private boolean blockMedia = true;
        private int snapshotMaxChars = 6_000;
        private boolean snapshotIncludeDetails = false;
        private boolean snapshotFilterVisibleOnly = true;
        private String executablePath = null;

        public Builder headless(boolean value) {
            this.headless = value;
            return this;
        }

        public Builder timeoutMs(int value) {
            this.timeoutMs = value;
            return this;
        }

        public Builder lowResourceMode(boolean value) {
            this.lowResourceMode = value;
            return this;
        }

        public Builder blockImages(boolean value) {
            this.blockImages = value;
            return this;
        }

        public Builder blockFonts(boolean value) {
            this.blockFonts = value;
            return this;
        }

        public Builder blockMedia(boolean value) {
            this.blockMedia = value;
            return this;
        }

        public Builder snapshotMaxChars(int value) {
            this.snapshotMaxChars = value;
            return this;
        }

        public Builder snapshotIncludeDetails(boolean value) {
            this.snapshotIncludeDetails = value;
            return this;
        }

        public Builder snapshotFilterVisibleOnly(boolean value) {
            this.snapshotFilterVisibleOnly = value;
            return this;
        }

        public Builder executablePath(String value) {
            this.executablePath = value;
            return this;
        }

        public BrowserConfig build() {
            return new BrowserConfig(headless, timeoutMs, lowResourceMode, blockImages, blockFonts, blockMedia, snapshotMaxChars, snapshotIncludeDetails, snapshotFilterVisibleOnly, executablePath);
        }
    }
}
