package io.github.dsheirer.limesdr;

/** Format of the samples on the USB or PCIe link, {@code lms_stream_t.linkFmt}. */
public enum LinkFormat {
    /** {@code LMS_LINK_FMT_DEFAULT}: currently 12-bit. */
    DEFAULT,
    /** {@code LMS_LINK_FMT_I16}: 16-bit integers. */
    I16,
    /** {@code LMS_LINK_FMT_I12}: packed 12-bit integers, a quarter less bandwidth than 16-bit. */
    I12;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }
}
