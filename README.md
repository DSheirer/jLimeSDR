# jLimeSDR

Java bindings for [LimeSuiteNG](https://github.com/myriadrf/LimeSuiteNG), the
driver for LimeSDR software-defined radios, built on the Java Foreign Function
& Memory API. They call LimeSuiteNG's C API (`lime/LimeSuite.h`, the `LMS_*`
functions) directly, without SoapySDR. The jar contains the native libraries,
so there is nothing else to install.

| OS | x86-64 | aarch64 |
|---------|----|----|
| Linux (glibc 2.34+, such as Ubuntu 22.04, Debian 12, RHEL 9) | ✓ | ✓ |
| macOS 11+ | ✓ | ✓ |
| Windows | ✓ | ✓ (FT601 devices only, see below) |

Requires Java 23 or later.

## Getting it

Download `jlimesdr-<version>.jar` from
[Releases](https://github.com/DSheirer/jLimeSDR/releases). The rolling
[`nightly`](https://github.com/DSheirer/jLimeSDR/releases/tag/nightly)
pre-release tracks `main` of this project and `develop` of upstream.

Check that your machine can see a device:

```
java -jar jlimesdr-<version>.jar
```

## Using it

```java
try (LimeSdrDevice device = LimeSdr.open()) {
    device.init();
    device.enableChannel(Direction.RX, 0, true);
    device.setSampleRate(10e6, 0);                          // 0: default oversampling
    device.setFrequency(Direction.RX, 0, 100e6);
    device.setAntenna(Direction.RX, 0, LimeSdrDevice.PATH_LNAW);
    device.setGain(Direction.RX, 0, 40);                    // 0-73 dB
    device.setLpfBandwidth(Direction.RX, 0, 10e6);
    device.calibrate(Direction.RX, 0, 10e6);                // after the other settings

    device.startRx(StreamConfig.of(0, SampleFormat.I16), 65536, buffer -> {
        short[] iq = buffer.copyShorts();                   // interleaved I, Q
        queue.offer(iq);
        return true;                                        // false stops receiving
    });
    Thread.sleep(10_000);
}
```

`LimeSdr.listDevices()` describes the connected devices, and
`LimeSdr.open(DeviceEntry)` or `LimeSdr.open(String serial)` opens one of them.
Configure a channel in this order: `init`, enable the channel, sample rate,
frequency, antenna, gain, filter, then `calibrate`, whose result depends on the
other settings.

`startRx` reads on a thread of its own and hands each buffer to the listener,
with the hardware timestamp of its first sample. Hand slow work to another
thread so the device's FIFO does not overflow. The listener may change
settings, such as the frequency, but not call `startRx`, `stopRx` or `close`.
`buffer.samples()` gives zero-copy access, valid only until the listener
returns; later use throws an exception. An exception thrown by a listener stops
receiving and is available from `receiveFailure()`.

### Streams

For several channels, or to read on your own thread, open the streams
directly. All streams of a device share one LimeSuiteNG stream, so open every
stream before starting any of them; with several, read them in turn.

```java
try (RxStream a = device.openRxStream(StreamConfig.of(0, SampleFormat.F32));
     RxStream b = device.openRxStream(StreamConfig.of(1, SampleFormat.F32))) {
    a.start();
    b.start();                                              // the device starts here
    float[] iqA = new float[2 * 65536];
    float[] iqB = new float[2 * 65536];
    while (running) {
        int n = a.read(iqA, 1000);                          // I/Q pairs read
        b.read(iqB, 1000);
        long timestamp = a.lastTimestamp();
    }
    StreamStatus status = a.status();                       // overruns, dropped packets
}
```

`TxStream.write` transmits, with `TxMetadata` to send at a hardware timestamp
or end a burst. Transmitting needs a license for the frequency you use, or a
dummy load.

### Other features

Per-amplifier gain through `writeParameter` with `Lms7Parameter.G_LNA_RFE`,
`G_TIA_RFE` and `G_PGA_RBB`; digital filters (GFIR), the NCO, test signals,
clocks and the external reference, the VCTCXO trim, GPIO, LMS7002M and FPGA
registers, configuration files, waveform playback and firmware/gateware
programming are also available. Every function in `LimeSuite.h` has a binding.

### Things to know about LimeSuiteNG

- `gain()` currently reads 12 dB more than `setGain()` set: upstream's
  `LMS_GetGaindB` still adds the offset of the original LimeSuite API, which
  `LMS_SetGaindB` no longer subtracts. jLimeSDR passes both values through.
- `setLpfBandwidth` and `calibrate` may adjust individual amplifiers, which
  changes the combined gain that `gain()` reports.
- Stream status counters are kept per device, not per stream, and reset on
  each `status()` call.
- LimeSuiteNG sizes each receive transfer for about 100 µs of samples, which
  leaves only 1-2 ms of transfers in flight on USB devices. Its receive thread
  asks for realtime priority, which an ordinary user doesn't get on Linux, so
  a busy system loses packets. jLimeSDR patches `LMS_SetupStream` (see
  [`native/patches`](native/patches)) so that a `throughputVsLatency` above 0.5
  selects the largest transfers the device allows: 16 packets per transfer on
  USB, about 8.7 ms in flight at 40 MS/s.
  `StreamConfig.withThroughputVsLatency(1)` turns this on; the default, 0.5,
  keeps upstream's behavior. Lost samples show up as gaps in
  `SampleBuffer.timestamp()`, which counts samples.
- A C++ exception that escapes a LimeSuiteNG C function aborts the whole JVM.
  jLimeSDR patches the two cases found so far into errors: `LMS_Open` on a
  device that another program has open, and `LMS_GetAntennaBW` on a port
  without a recommended range (None and the loopback ports). Both now throw
  `LimeSdrException`.

### Logging

LimeSuiteNG's messages go to the `System.Logger` named
`io.github.dsheirer.limesdr.LimeSuiteNG`, which Java's logging, SLF4J and Log4j
can all receive. `LimeSdr.setLogHandler` replaces that. The message of a
`LimeSdrException` includes the errors that the failing call logged.

### Native access

Java prints a warning, which future versions will turn into an error, unless
you allow native access:

```
java --enable-native-access=ALL-UNNAMED -cp app.jar:jlimesdr.jar my.App
```

On the module path, use `--enable-native-access=io.github.dsheirer.limesdr`. In
your own executable jar, add `Enable-Native-Access: ALL-UNNAMED` to the manifest.

### Device access

- **Linux:** install upstream's
  [udev rules](https://github.com/myriadrf/LimeSuiteNG/tree/develop/udev-rules)
  to use a device without root. PCIe devices (LimeSDR XTRX, X3) also need
  upstream's `limepcie` kernel driver.
- **Windows:** the LimeSDR-USB needs the Cypress FX3 driver (CyUSB3), and FT601
  devices such as the LimeSDR Mini need FTDI's D3XX driver; on Windows 11 the
  WinUSB-based D3XX driver is used. The Windows on Arm library supports only
  FT601 devices, because the Cypress FX3 SDK has no ARM64 library.
- **macOS:** USB devices work without drivers.

### Native library location

Libraries are extracted to the temporary directory at startup. Set
`-Djlimesdr.library.path=<dir>` to load them from a directory instead: for
example, to supply your own build of libusb, or if the temporary directory is
mounted `noexec`.

## Building

You need JDK 23+, CMake 3.19+, a C and C++ compiler, make and git. On Linux,
the static C++ runtime (`libstdc++-static` on RHEL-like systems). On Windows,
Visual Studio 2022.

```
./native/build-unix.sh                                # Linux and macOS
pwsh native/build-windows.ps1 -Arch x64               # Windows; or -Arch ARM64
./gradlew build                                       # jar in build/libs
./gradlew test -PhardwareTest=true                    # also test a connected device
```

A local jar contains only your platform. The hardware tests are receive-only:
they transmit nothing and write nothing to the device's flash memory.

LimeSuiteNG is built with its core as a static library, and
[`native/jlimesdr.cmake`](native/jlimesdr.cmake) links it into one shared
library, `limesuiteng-legacyapi`, which exports only the `LMS_*` functions.
Keeping LimeSuiteNG's C++ interfaces inside one library lets the C++ runtime be
linked statically on Linux and Windows. libusb stays a separate library on
Linux and macOS; Windows uses the Cypress and FTDI libraries instead, linked in.

## Releases

jLimeSDR versions follow the LimeSuiteNG project version: jLimeSDR 25.1.0
wraps LimeSuiteNG 25.1.0 (`LMS_GetLibraryVersion()` reports the library
version, such as `0.3.0-g6c3feee9`). [`native/versions.env`](native/versions.env)
pins the upstream commit (`LIMESUITENG_REF`) and the version it names
(`LIMESUITENG_VERSION`); the build fails if they disagree. The pin is a commit
on upstream `develop`, because the `v25.1.0` tag predates many fixes to the
`LMS_*` API.

| Trigger | What happens |
|---------|--------------|
| Pull request | Builds and tests all six platforms; publishes nothing |
| Merge to `main` | Replaces the `nightly` pre-release, built against upstream `develop` |
| Daily schedule | The same, if this project or upstream has changed |
| Tag `vX.Y.Z` | Publishes a release built from the pinned upstream commit |

To release, tag the pinned version: `git tag v25.1.0 && git push origin v25.1.0`.
The workflow rejects a tag that does not match the pin. For a later build on
the same upstream version, such as a newer pinned commit or a Java-only fix,
add a fourth number, such as `v25.1.0.1`; for a pre-release add a suffix, such
as `v25.1.0-rc1`.

To adopt a newer upstream commit, change `LIMESUITENG_REF`, and
`LIMESUITENG_VERSION` if upstream's version changed, in `native/versions.env`.
If upstream added functions to `LimeSuite.h`, the build lists them; add them to
`Native.java`. Nightly builds only warn about them.

## License

Apache License 2.0. The bundled native libraries have their own licenses: see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
