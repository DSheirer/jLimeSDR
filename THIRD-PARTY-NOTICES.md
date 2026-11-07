# Third-party notices

jLimeSDR is licensed under the Apache License 2.0. The jar also contains native
libraries from the projects below, which keep their own licenses. Full license
texts are in `META-INF/licenses/` inside the jar.

## LimeSuiteNG

Source: <https://github.com/myriadrf/LimeSuiteNG>

Licensed under the Apache License 2.0. See `META-INF/licenses/limesuiteng-LICENSE`.
The exact commit in each jar is recorded in
`jlimesdr/native/<platform>/build-info.properties`. LimeSuiteNG includes
kissfft (BSD-3-Clause) and other bundled sources under their own permissive
licenses; see its `external/` directory. jLimeSDR is an independent project
and is not endorsed by Lime Microsystems. LimeSDR is a trademark of Lime
Microsystems.

## libusb (Linux and macOS)

Source: <https://github.com/libusb/libusb>

Licensed under the GNU Lesser General Public License version 2.1 or later. See
`META-INF/licenses/libusb-COPYING`. The exact version in each jar is recorded in
`jlimesdr/native/<platform>/build-info.properties`, and `native/versions.env` in
this repository records the release it was built from.

libusb is shipped as a separate shared library so it can be replaced: point the
`jlimesdr.library.path` system property at a directory containing your own
build along with `liblimesuiteng-legacyapi`.

## Cypress FX3 SDK CyAPI (Windows x86-64 only)

Source: the FX3 SDK archive that LimeSuiteNG's build downloads, recorded in
`native/versions.env`.

Proprietary; Cypress (now Infineon) permits distributing it in object code
form with products that use Cypress devices. It is linked into
`limesuiteng-legacyapi.dll` for the LimeSDR-USB, whose USB controller is a
Cypress FX3. See `META-INF/licenses/cypress-fx3sdk-LICENSE.txt`.

## FTDI D3XX (Windows only)

Source: `external/WU_FTD3XXLib.zip` in the LimeSuiteNG repository.

Proprietary library from Future Technology Devices International, linked into
`limesuiteng-legacyapi.dll` for FT601 devices such as the LimeSDR Mini. FTDI
provides its driver libraries for use with FTDI devices.
