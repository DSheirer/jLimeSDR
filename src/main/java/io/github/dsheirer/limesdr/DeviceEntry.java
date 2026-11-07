package io.github.dsheirer.limesdr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A connected device, from {@link LimeSdr#listDevices()}.
 *
 * @param descriptor the library's description of the device, an {@code lms_info_str_t}
 *     such as {@code LimeSDR-USB, media=USB3.0, addr=1d50:6108, serial=0009060B00462008},
 *     which {@link LimeSdr#open(DeviceEntry)} passes back to the library
 * @param name the device name, such as {@code LimeSDR-USB}
 * @param media the connection, such as {@code USB3.0} or {@code PCIe}, or empty
 * @param address the bus address, or empty
 * @param serialNumber the serial number as reported, or empty
 */
public record DeviceEntry(String descriptor, String name, String media, String address, String serialNumber) {
    private static final Pattern FIELD = Pattern.compile(", (media|addr|serial)=");

    /** Parses a device description string. */
    public static DeviceEntry parse(String descriptor) {
        Matcher matcher = FIELD.matcher(descriptor);
        String name = null;
        String media = "";
        String address = "";
        String serial = "";
        String key = null;
        int start = 0;
        while (true) {
            boolean found = matcher.find();
            String value = descriptor.substring(start, found ? matcher.start() : descriptor.length());
            if (key == null) {
                name = value;
            } else {
                switch (key) {
                    case "media" -> media = value;
                    case "addr" -> address = value;
                    default -> serial = value;
                }
            }
            if (!found) {
                break;
            }
            key = matcher.group(1);
            start = matcher.end();
        }
        return new DeviceEntry(descriptor, name, media, address, serial);
    }

    /**
     * Returns true if this device's serial number is the given one, ignoring
     * case and leading zeros.
     */
    public boolean hasSerialNumber(String serial) {
        return !serialNumber.isEmpty() && stripZeros(serialNumber).equalsIgnoreCase(stripZeros(serial));
    }

    private static String stripZeros(String value) {
        int i = 0;
        while (i < value.length() - 1 && value.charAt(i) == '0') {
            i++;
        }
        return value.substring(i);
    }

    @Override
    public String toString() {
        return descriptor;
    }
}
