package io.github.dsheirer.limesdr;

/**
 * A bit field in an LMS7002M register, {@code struct LMS7Parameter}, for
 * {@link LimeSdrDevice#readParameter} and {@link LimeSdrDevice#writeParameter}.
 *
 * <p>Parameters of the receive and transmit chains act on the channel that
 * {@link #MAC} selects: write 1 to it for channel A (0) or 2 for channel B (1)
 * first. The constants here are the ones that {@code LimeSuite.h} points to for
 * setting individual amplifier gains; others are in upstream's
 * {@code LMS7002M_parameters.h}.
 *
 * @param address register address
 * @param msb most significant bit of the field
 * @param lsb least significant bit of the field
 * @param defaultValue value after reset
 * @param name upstream's name for the parameter
 */
public record Lms7Parameter(int address, int msb, int lsb, int defaultValue, String name) {
    /** {@code MAC}: selects the channel that channel parameters act on, 1 for A and 2 for B. */
    public static final Lms7Parameter MAC = new Lms7Parameter(0x0020, 1, 0, 3, "MAC");
    /** {@code G_LNA_RFE}: receive LNA gain, 1 to 15. */
    public static final Lms7Parameter G_LNA_RFE = new Lms7Parameter(0x0113, 9, 6, 15, "G_LNA_RFE");
    /** {@code G_TIA_RFE}: receive TIA gain, 1 to 3. */
    public static final Lms7Parameter G_TIA_RFE = new Lms7Parameter(0x0113, 1, 0, 3, "G_TIA_RFE");
    /** {@code G_PGA_RBB}: receive PGA gain, 0 to 31 (-12 to 19 dB). */
    public static final Lms7Parameter G_PGA_RBB = new Lms7Parameter(0x0119, 4, 0, 11, "G_PGA_RBB");
    /** {@code LOSS_LIN_TXPAD_TRF}: attenuation of the linearizing part of the transmit PAD. */
    public static final Lms7Parameter LOSS_LIN_TXPAD_TRF = new Lms7Parameter(0x0101, 10, 6, 0, "LOSS_LIN_TXPAD_TRF");
    /** {@code LOSS_MAIN_TXPAD_TRF}: attenuation of the main transmit PAD. */
    public static final Lms7Parameter LOSS_MAIN_TXPAD_TRF = new Lms7Parameter(0x0101, 5, 1, 0, "LOSS_MAIN_TXPAD_TRF");

    /** Checks the field. */
    public Lms7Parameter {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Register address out of range: " + address);
        }
        if (lsb < 0 || msb > 15 || msb < lsb) {
            throw new IllegalArgumentException("Invalid bit field " + msb + ":" + lsb);
        }
        if (name == null) {
            name = "";
        }
    }

    /** Returns the largest value the field holds. */
    public int maxValue() {
        return (1 << (msb - lsb + 1)) - 1;
    }
}
