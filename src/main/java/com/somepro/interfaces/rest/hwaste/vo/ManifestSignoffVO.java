package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签收单对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；delFlag / createBy / updateBy / updateTime 不进 API 契约。
 */
public record ManifestSignoffVO(
        Long id,
        String signoffNo,
        Long manifestId,
        BigDecimal receivedWeight,
        BigDecimal disposedWeight,
        String disposalMethod,
        LocalDateTime signAt,
        LocalDateTime confirmAt,
        LocalDateTime createTime) implements Serializable {
}
