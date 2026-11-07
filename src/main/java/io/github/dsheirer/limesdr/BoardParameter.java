package io.github.dsheirer.limesdr;

/**
 * A custom board parameter read with {@link LimeSdrDevice#readBoardParameter}.
 *
 * @param value the parameter's value
 * @param units its units, or an empty string
 */
public record BoardParameter(double value, String units) {
    /** {@code BOARD_PARAM_DAC}: the runtime VCTCXO trim DAC value, which does not persist over a power cycle. */
    public static final int DAC = 0;

    /** {@code BOARD_PARAM_TEMP}: the board temperature sensor, if present. Read-only. */
    public static final int TEMPERATURE = 1;
}
