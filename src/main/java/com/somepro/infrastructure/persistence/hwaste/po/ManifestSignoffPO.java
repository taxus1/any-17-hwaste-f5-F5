package com.somepro.infrastructure.persistence.hwaste.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * t_manifest_signoff 表的持久化对象（PO，基础设施层）。只描述表形状，不放业务规则。
 * 一张联单签收时落一行：实际过磅重量与签收时刻都记在这张签收单上。
 */
@Getter
@Setter
@TableName("t_manifest_signoff")
public class ManifestSignoffPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("signoff_no")
    private String signoffNo;

    @TableField("manifest_id")
    private Long manifestId;

    /** 实际签收重量（千克）：认过磅的数，不拿申报量硬顶。 */
    @TableField("received_weight")
    private BigDecimal receivedWeight;

    @TableField("disposed_weight")
    private BigDecimal disposedWeight;

    @TableField("disposal_method")
    private String disposalMethod;

    @TableField("sign_at")
    private LocalDateTime signAt;

    @TableField("confirm_at")
    private LocalDateTime confirmAt;
}
