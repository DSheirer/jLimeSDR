package io.github.dsheirer.limesdr.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;

/**
 * Memory layouts of the structures in {@code LimeSuite.h} and
 * {@code LMS7002M_parameters.h}. Every supported platform is 64-bit with
 * one-byte {@code bool} and four-byte enums, so one layout fits all of them.
 */
public final class Layouts {
    /** {@code lms_info_str_t}: a device description string. */
    public static final long INFO_STRING_SIZE = 256;

    /** {@code lms_name_t}: an antenna or programming mode name. */
    public static final long NAME_SIZE = 64;

    /** {@code lms_range_t}. */
    public static final StructLayout RANGE = MemoryLayout.structLayout(
        JAVA_DOUBLE.withName("min"),
        JAVA_DOUBLE.withName("max"),
        JAVA_DOUBLE.withName("step")).withName("lms_range_t");

    /** {@code lms_dev_info_t}. */
    public static final StructLayout DEVICE_INFO = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(32, JAVA_BYTE).withName("deviceName"),
        MemoryLayout.sequenceLayout(32, JAVA_BYTE).withName("expansionName"),
        MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("firmwareVersion"),
        MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("hardwareVersion"),
        MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("protocolVersion"),
        JAVA_LONG.withName("boardSerialNumber"),
        MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("gatewareVersion"),
        MemoryLayout.sequenceLayout(32, JAVA_BYTE).withName("gatewareTargetBoard")).withName("lms_dev_info_t");

    /** {@code lms_stream_t}. */
    public static final StructLayout STREAM = MemoryLayout.structLayout(
        JAVA_LONG.withName("handle"),
        JAVA_BOOLEAN.withName("isTx"),
        MemoryLayout.paddingLayout(3),
        JAVA_INT.withName("channel"),
        JAVA_INT.withName("fifoSize"),
        JAVA_FLOAT.withName("throughputVsLatency"),
        JAVA_INT.withName("dataFmt"),
        JAVA_INT.withName("linkFmt")).withName("lms_stream_t");

    /** {@code lms_stream_meta_t}. */
    public static final StructLayout STREAM_META = MemoryLayout.structLayout(
        JAVA_LONG.withName("timestamp"),
        JAVA_BOOLEAN.withName("waitForTimestamp"),
        JAVA_BOOLEAN.withName("flushPartialPacket"),
        MemoryLayout.paddingLayout(6)).withName("lms_stream_meta_t");

    /** {@code lms_stream_status_t}. */
    public static final StructLayout STREAM_STATUS = MemoryLayout.structLayout(
        JAVA_BOOLEAN.withName("active"),
        MemoryLayout.paddingLayout(3),
        JAVA_INT.withName("fifoFilledCount"),
        JAVA_INT.withName("fifoSize"),
        JAVA_INT.withName("underrun"),
        JAVA_INT.withName("overrun"),
        JAVA_INT.withName("droppedPackets"),
        JAVA_DOUBLE.withName("sampleRate"),
        JAVA_DOUBLE.withName("linkRate"),
        JAVA_LONG.withName("timestamp")).withName("lms_stream_status_t");

    /** {@code struct LMS7Parameter}, passed by value. */
    public static final StructLayout LMS7_PARAMETER = MemoryLayout.structLayout(
        JAVA_SHORT.withName("address"),
        JAVA_BYTE.withName("msb"),
        JAVA_BYTE.withName("lsb"),
        JAVA_SHORT.withName("defaultValue"),
        MemoryLayout.paddingLayout(2),
        ADDRESS.withName("name"),
        ADDRESS.withName("tooltip")).withName("LMS7Parameter");

    private Layouts() {
    }

    /** Returns the byte offset of a member of a structure. */
    public static long offset(StructLayout layout, String member) {
        return layout.byteOffset(PathElement.groupElement(member));
    }
}
