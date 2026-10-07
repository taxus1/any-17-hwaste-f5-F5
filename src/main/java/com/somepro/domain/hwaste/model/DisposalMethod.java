package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;

import java.util.Locale;

/**
 * 处置方式（纯领域枚举）：INCINERATE 焚烧 / LANDFILL 填埋 / UTILIZE 利用 /
 * CEMENT 水泥窑协同 / OTHER 其他。
 */
public enum DisposalMethod {

    /** 焚烧。 */
    INCINERATE,
    /** 填埋。 */
    LANDFILL,
    /** 利用。 */
    UTILIZE,
    /** 水泥窑协同。 */
    CEMENT,
    /** 其他。 */
    OTHER;

    /**
     * 归一化并校验处置方式：去空白、转大写；不在五种之内的挡回。
     * 返回归一化后的编码（领域对象里 disposalMethod 仍以 String 存放）。
     */
    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("处置方式不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return DisposalMethod.valueOf(normalized).name();
        } catch (IllegalArgumentException e) {
            throw new BizException("处置方式不合法：" + raw + "（仅支持 INCINERATE/LANDFILL/UTILIZE/CEMENT/OTHER）");
        }
    }
}
