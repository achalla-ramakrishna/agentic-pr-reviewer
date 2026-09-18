package com.codewalnut.prreviewer.dto;

/**
 * Accept/reject feedback on a set of findings. precision is
 * accepted / (accepted + rejected) -- null when nothing has been decided yet,
 * since 0/0 isn't a meaningful rate. open findings are included for context
 * but don't factor into precision (per SPEC, only reviewer decisions do).
 */
public record PrecisionStats(long accepted, long rejected, long open, Double precision) {

    public static PrecisionStats of(long accepted, long rejected, long open) {
        long decided = accepted + rejected;
        Double precision = decided == 0 ? null : (double) accepted / decided;
        return new PrecisionStats(accepted, rejected, open, precision);
    }
}
