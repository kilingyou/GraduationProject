package com.scm.common;

/**
 * Shared string constants for roles, orders, and device lifecycle.
 */
public final class Constants {

    private Constants() {
    }

    // --- Role keys (align with security / JWT claims) ---

    public static final String SUPPLIER = "SUPPLIER";
    public static final String MANUFACTURER = "MANUFACTURER";
    public static final String ASSEMBLER = "ASSEMBLER";
    public static final String DISTRIBUTOR = "DISTRIBUTOR";
    public static final String REGULATOR = "REGULATOR";
    public static final String ENDUSER = "ENDUSER";

    // --- Order statuses ---

    public static final String PENDING_ACCEPTANCE = "PENDING_ACCEPTANCE";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String IN_PRODUCTION = "IN_PRODUCTION";
    public static final String PENDING_QC = "PENDING_QC";
    public static final String QC_FAILED = "QC_FAILED";
    public static final String COMPLETED = "COMPLETED";
    public static final String PENDING_SHIPMENT = "PENDING_SHIPMENT";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    // --- Device / unit lifecycle statuses ---

    public static final String PRODUCED = "PRODUCED";
    public static final String QC_PASS = "QC_PASS";
    public static final String ASSEMBLED = "ASSEMBLED";
    public static final String SOLD = "SOLD";
    public static final String DECOMMISSIONED = "DECOMMISSIONED";

    // --- 不合格处置（RejectRecord.disposalType / disposalStatus）---

    /** 退货至供应商 */
    public static final String DISPOSAL_RETURN = "RETURN";
    /** 就地销毁 */
    public static final String DISPOSAL_DESTROY = "DESTROY";

    /** 待供应商确认退货收讫 */
    public static final String DISPOSAL_AWAITING_SUPPLIER = "AWAITING_SUPPLIER";
    /** 待制造商确认已销毁 */
    public static final String DISPOSAL_AWAITING_MFG_DESTROY = "AWAITING_MFG_DESTROY";
    /** 处置已闭环 */
    public static final String DISPOSAL_COMPLETED = "COMPLETED";
}
