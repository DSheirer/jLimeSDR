package io.github.dsheirer.limesdr;

import java.util.List;

/** Command line tool that prints the library version and the connected devices. */
public final class LimeSdrInfo {
    private LimeSdrInfo() {
    }

    /** Runs the tool. */
    public static void main(String[] args) {
        // Library messages would interleave with the report.
        LimeSdr.setLogHandler((level, message) -> {
            if (level.compareTo(LogLevel.WARNING) <= 0) {
                System.err.println("LimeSuiteNG " + level + ": " + message);
            }
        });
        System.out.println("LimeSuiteNG " + LimeSdr.libraryVersion());
        List<DeviceEntry> devices;
        try {
            devices = LimeSdr.listDevices();
        } catch (LimeSdrException e) {
            // Machines without USB access, such as containers, end up here.
            System.out.println("Unable to list devices: " + e.getMessage());
            return;
        }
        System.out.println("Devices found: " + devices.size());
        for (int i = 0; i < devices.size(); i++) {
            DeviceEntry entry = devices.get(i);
            System.out.println("Device " + i + ": " + entry.name());
            System.out.println("  Descriptor:    " + entry.descriptor());
            try (LimeSdrDevice device = LimeSdr.open(entry)) {
                DeviceInfo info = device.deviceInfo();
                System.out.println("  Serial number: " + info.serialNumberHex());
                System.out.println("  Firmware:      " + info.firmwareVersion() + ", gateware " + info.gatewareVersion()
                    + ", hardware " + info.hardwareVersion() + ", protocol " + info.protocolVersion());
                if (!info.expansionName().isEmpty() && !info.expansionName().equalsIgnoreCase("UNSUPPORTED")) {
                    System.out.println("  Expansion:     " + info.expansionName());
                }
                for (Direction direction : Direction.values()) {
                    int channels = device.channelCount(direction);
                    Range frequency = device.frequencyRange(direction);
                    Range rate = device.sampleRateRange(direction);
                    System.out.printf("  %s: %d channels, %.0f-%.0f MHz, %.3f-%.3f MS/s, antennas %s%n", direction,
                        channels, frequency.min() / 1e6, frequency.max() / 1e6, rate.min() / 1e6, rate.max() / 1e6,
                        channels > 0 ? device.antennas(direction, 0) : List.of());
                }
            } catch (LimeSdrException | RuntimeException e) {
                System.out.println("  Unable to open: " + e.getMessage());
            }
        }
    }
}
