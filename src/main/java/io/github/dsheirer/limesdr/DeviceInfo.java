package io.github.dsheirer.limesdr;

/**
 * Identification and versions of an open device, {@code lms_dev_info_t}.
 *
 * @param deviceName display name, such as {@code LimeSDR-USB}
 * @param expansionName name of the expansion board, or empty
 * @param firmwareVersion firmware version
 * @param hardwareVersion hardware revision
 * @param protocolVersion communication protocol version
 * @param boardSerialNumber unique board serial number
 * @param gatewareVersion FPGA gateware version and revision
 * @param gatewareTargetBoard board the gateware was built for
 */
public record DeviceInfo(String deviceName, String expansionName, String firmwareVersion, String hardwareVersion,
        String protocolVersion, long boardSerialNumber, String gatewareVersion, String gatewareTargetBoard) {
    /** Returns the serial number as 16 hexadecimal digits, the form device descriptions use. */
    public String serialNumberHex() {
        return String.format("%016X", boardSerialNumber);
    }
}
