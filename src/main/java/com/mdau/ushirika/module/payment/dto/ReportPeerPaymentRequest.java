package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.enums.PaymentMode;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ReportPeerPaymentRequest(

    @NotNull(message = "Payment mode is required.")
    PaymentMode paymentMode,

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "1.00", message = "Amount must be at least $1.00.")
    BigDecimal amount,

    @NotBlank(message = "Transaction reference is required.")
    @Size(min = 4, max = 100, message = "Transaction reference must be between 4 and 100 characters.")
    String memberTxReference,

    /** Contribution period — e.g. "2025-06". Optional for non-dues payments. */
    @Size(max = 10)
    String period,

    @Size(max = 500)
    String notes,

    /** Defaults to DUES when omitted — set REGISTRATION_FEE for the onboarding registration payment. */
    PeerPaymentPurpose purpose,

    /**
     * Cloudinary URL of a screenshot proving the payment was sent. Optional at the API level so
     * automated/API testing isn't blocked — the real frontend form should treat it as required.
     */
    @Size(max = 1000)
    String proofImageUrl
) {}
