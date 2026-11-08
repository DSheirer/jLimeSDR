package io.github.dsheirer.limesdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Receive-only tests against a real device. Enable with {@code -PhardwareTest=true}.
 * Nothing is transmitted and nothing is written to the device's flash memory.
 */
@EnabledIfSystemProperty(named = "limesdr.hardware.test", matches = "true")
class HardwareTest {
    private static final double FREQUENCY = 100e6;
    private static final double SAMPLE_RATE = 10e6;
    private static final int BUFFER = 65536;

    private static void configure(LimeSdrDevice device) throws LimeSdrException {
        device.init();
        device.enableChannel(Direction.RX, 0, true);
        device.setSampleRate(SAMPLE_RATE, 0);
        device.setFrequency(Direction.RX, 0, FREQUENCY);
        device.setAntenna(Direction.RX, 0, LimeSdrDevice.PATH_LNAW);
        device.setGain(Direction.RX, 0, 40);
        device.setLpfBandwidth(Direction.RX, 0, SAMPLE_RATE);
        device.calibrate(Direction.RX, 0, SAMPLE_RATE);
    }

    @Test
    void readsDeviceInformation() throws LimeSdrException {
        List<DeviceEntry> devices = LimeSdr.listDevices();
        assertFalse(devices.isEmpty(), "No device found");
        DeviceEntry entry = devices.getFirst();
        try (LimeSdrDevice device = LimeSdr.open(entry)) {
            DeviceInfo info = device.deviceInfo();
            assertFalse(info.deviceName().isBlank());
            assertFalse(info.firmwareVersion().isBlank());
            if (!entry.serialNumber().isEmpty()) {
                assertTrue(entry.hasSerialNumber(info.serialNumberHex()), entry + " vs " + info);
            }
            assertTrue(device.channelCount(Direction.RX) >= 1);
            assertTrue(device.frequencyRange(Direction.RX).contains(FREQUENCY));
            assertTrue(device.sampleRateRange(Direction.RX).contains(SAMPLE_RATE));
            assertFalse(device.antennas(Direction.RX, 0).isEmpty());
            double temperature = device.chipTemperature();
            assertTrue(temperature > 0 && temperature < 100, "Temperature " + temperature);
            assertThrows(LimeSdrException.class, () -> device.setFrequency(Direction.RX, 99, FREQUENCY));
        }
        if (!entry.serialNumber().isEmpty()) {
            try (LimeSdrDevice device = LimeSdr.open(entry.serialNumber())) {
                assertTrue(entry.hasSerialNumber(device.deviceInfo().serialNumberHex()));
            }
        }
        assertThrows(LimeSdrException.class, () -> LimeSdr.open("FFFFFFFFFFFFFFFF"));
    }

    @Test
    void antennaPortsWithoutARangeAreAnError() throws LimeSdrException {
        // Upstream threw std::out_of_range through the C API for these, which
        // aborted the JVM; native/patches/0002 makes it an error.
        try (LimeSdrDevice device = LimeSdr.open()) {
            List<String> antennas = device.antennas(Direction.RX, 0);
            for (int index = 0; index < antennas.size(); index++) {
                int port = index;
                String name = antennas.get(port);
                if (name.equals("None") || name.startsWith("LB")) {
                    assertThrows(LimeSdrException.class, () -> device.antennaBandwidth(Direction.RX, 0, port), name);
                } else {
                    assertTrue(device.antennaBandwidth(Direction.RX, 0, port).max() > 0, name);
                }
            }
        }
    }

    @Test
    void openingADeviceThatIsInUseIsAnError() throws LimeSdrException {
        // Upstream let the USB connection's exception through LMS_Open, which
        // aborted the JVM; native/patches/0002 makes it an error.
        try (LimeSdrDevice device = LimeSdr.open()) {
            assertThrows(LimeSdrException.class, LimeSdr::open);
            assertFalse(device.deviceInfo().deviceName().isBlank());
        }
        try (LimeSdrDevice device = LimeSdr.open()) {
            assertFalse(device.isClosed());
        }
    }

    @Test
    void appliesSettings() throws LimeSdrException {
        try (LimeSdrDevice device = LimeSdr.open()) {
            configure(device);
            SampleRate rate = device.sampleRate(Direction.RX, 0);
            assertEquals(SAMPLE_RATE, rate.host(), 10);
            assertTrue(rate.rf() >= rate.host());
            assertEquals(FREQUENCY, device.frequency(Direction.RX, 0), 10);
            assertEquals(LimeSdrDevice.PATH_LNAW, device.antenna(Direction.RX, 0));
            // The filter and calibration adjust individual amplifiers, so the
            // combined gain is read back right after setting it. LMS_GetGaindB
            // adds 12 dB that LMS_SetGaindB does not subtract; this fails if
            // upstream changes that, so the documentation can follow.
            device.setGain(Direction.RX, 0, 30);
            assertEquals(42, device.gain(Direction.RX, 0), 1);
            device.setFrequency(Direction.RX, 0, 433.92e6);
            assertEquals(433.92e6, device.frequency(Direction.RX, 0), 10);
            assertEquals(TestSignal.NONE, device.testSignal(Direction.RX, 0));

            device.writeParameter(Lms7Parameter.MAC, 1);
            int pga = device.readParameter(Lms7Parameter.G_PGA_RBB);
            device.writeParameter(Lms7Parameter.G_PGA_RBB, 20);
            assertEquals(20, device.readParameter(Lms7Parameter.G_PGA_RBB));
            device.writeParameter(Lms7Parameter.G_PGA_RBB, pga);
            assertThrows(IllegalArgumentException.class, () -> device.writeParameter(Lms7Parameter.G_TIA_RFE, 4));
            assertThrows(IllegalArgumentException.class, () -> device.setSampleRate(SAMPLE_RATE, 3));
        }
    }

    @Test
    void streamsSamplesWithoutGaps() throws Exception {
        try (LimeSdrDevice device = LimeSdr.open()) {
            configure(device);
            CountDownLatch received = new CountDownLatch(100);
            AtomicInteger gaps = new AtomicInteger();
            AtomicLong expected = new AtomicLong(-1);
            AtomicReference<short[]> last = new AtomicReference<>();
            device.startRx(StreamConfig.of(0, SampleFormat.I16), BUFFER, buffer -> {
                long next = expected.getAndSet(buffer.timestamp() + buffer.sampleCount());
                if (next >= 0 && next != buffer.timestamp()) {
                    gaps.incrementAndGet();
                }
                last.set(buffer.copyShorts());
                received.countDown();
                return true;
            });
            assertTrue(received.await(10, TimeUnit.SECONDS), "Samples did not arrive");
            assertTrue(device.isReceiving());
            assertThrows(IllegalStateException.class, () -> device.startRx(StreamConfig.of(0, SampleFormat.I16),
                BUFFER, buffer -> true));
            device.stopRx();
            assertFalse(device.isReceiving());
            assertNull(device.receiveFailure());
            assertEquals(0, gaps.get(), "Timestamp gaps");
            assertEquals(BUFFER * 2, last.get().length);
            boolean nonZero = false;
            for (short sample : last.get()) {
                nonZero |= sample != 0;
            }
            assertTrue(nonZero, "Samples were all zero");
        }
    }

    @Test
    void canRestartAfterListenerEndedStream() throws Exception {
        try (LimeSdrDevice device = LimeSdr.open()) {
            configure(device);
            for (int run = 0; run < 3; run++) {
                CountDownLatch received = new CountDownLatch(1);
                device.startRx(StreamConfig.of(0, SampleFormat.F32), BUFFER, buffer -> {
                    received.countDown();
                    return false;
                });
                assertTrue(received.await(10, TimeUnit.SECONDS), "No samples in run " + run);
            }
            device.stopRx();
            assertNull(device.receiveFailure());
        }
    }

    @Test
    void listenerCanTuneButNotStop() throws Exception {
        try (LimeSdrDevice device = LimeSdr.open()) {
            configure(device);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> problem = new AtomicReference<>();
            device.startRx(StreamConfig.of(0, SampleFormat.I16), BUFFER, buffer -> {
                try {
                    device.setFrequency(Direction.RX, 0, FREQUENCY + 1e6);
                    assertThrows(IllegalStateException.class, device::stopRx);
                    assertThrows(IllegalStateException.class, device::close);
                } catch (Throwable t) {
                    problem.set(t);
                }
                done.countDown();
                return false;
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            device.stopRx();
            assertNull(problem.get());
            assertEquals(FREQUENCY + 1e6, device.frequency(Direction.RX, 0), 10);
        }
    }

    @Test
    void readsTwoChannels() throws Exception {
        try (LimeSdrDevice device = LimeSdr.open()) {
            if (device.channelCount(Direction.RX) < 2) {
                return;
            }
            configure(device);
            device.enableChannel(Direction.RX, 1, true);
            device.setAntenna(Direction.RX, 1, LimeSdrDevice.PATH_LNAW);
            device.setGain(Direction.RX, 1, 40);
            try (RxStream a = device.openRxStream(StreamConfig.of(0, SampleFormat.F32));
                 RxStream b = device.openRxStream(StreamConfig.of(1, SampleFormat.F32))) {
                a.start();
                b.start();
                float[] iqA = new float[2 * BUFFER];
                float[] iqB = new float[2 * BUFFER];
                for (int i = 0; i < 50; i++) {
                    int readA = a.read(iqA, 1000);
                    int readB = b.read(iqB, 1000);
                    assertEquals(BUFFER, readA);
                    assertEquals(readA, readB);
                    assertEquals(a.lastTimestamp(), b.lastTimestamp());
                }
                assertEquals(0, a.status().droppedPackets());
            }
        }
    }

    @Test
    void readsStreamDirectly() throws Exception {
        try (LimeSdrDevice device = LimeSdr.open()) {
            configure(device);
            try (RxStream stream = device.openRxStream(StreamConfig.of(0, SampleFormat.I12))) {
                assertThrows(IllegalStateException.class, () -> device.openRxStream(StreamConfig.of(0,
                    SampleFormat.I12)));
                stream.start();
                assertThrows(IllegalStateException.class, () -> device.openRxStream(StreamConfig.of(1,
                    SampleFormat.I12)));
                short[] iq = new short[BUFFER * 2];
                long total = 0;
                long start = System.nanoTime();
                while (System.nanoTime() - start < 2_000_000_000L) {
                    total += stream.read(iq, 1000);
                }
                double rate = total / ((System.nanoTime() - start) / 1e9);
                assertEquals(SAMPLE_RATE, rate, SAMPLE_RATE * 0.05, "Sample rate " + rate);
                StreamStatus status = stream.status();
                assertTrue(stream.isStarted());
                assertEquals(0, status.droppedPackets());
                assertThrows(IllegalStateException.class, () -> stream.read(new float[2], 10));
                stream.stop();
            }
        }
    }
}
