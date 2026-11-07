package io.github.dsheirer.limesdr;

/** Receive or transmit, the {@code dir_tx} argument of the native functions. */
public enum Direction {
    /** Receive, {@code LMS_CH_RX}. */
    RX(false),
    /** Transmit, {@code LMS_CH_TX}. */
    TX(true);

    private final boolean tx;

    Direction(boolean tx) {
        this.tx = tx;
    }

    /** Returns the native {@code dir_tx} value. */
    public boolean isTx() {
        return tx;
    }
}
