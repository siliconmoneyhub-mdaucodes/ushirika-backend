package com.mdau.ushirika.module.benevolence.enums;

public enum EnrollmentStatus {
    /** Enrolled but $600 not yet fully paid. */
    PAYING,
    /** $600 paid; 6-month probation period in progress. */
    PROBATION,
    /** Probation complete; member may submit claims. */
    ELIGIBLE,
    /** The join request this enrollment was opened for (at form-send time) was later rejected --
     *  no further payments accepted, and the member's own view treats this the same as never
     *  having enrolled. See BenevolenceEnrollmentService#rejectJoinRequest. */
    CANCELLED
}
