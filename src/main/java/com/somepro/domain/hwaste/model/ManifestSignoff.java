package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签收与处置确认单（纯领域实体）。
 *
 * 一张签收单一条记录，挂在一张联单下：货签收进厂时落实际过磅重量与签收时刻；
 * 货在处置设施里真正处理掉后，再补上实际处置重量、处置方式与确认时刻，这一票才算走到头。
 * 签收单编号形如 SO-2026-0001，由仓储层分配，全局唯一。
 *
 * 不变量集中在这里：
 * - 处置确认只办一回：已确认过（确认时刻已落）的签收单不能再确认；
 * - 实际处置重量必须大于 0，且不该多过当初签收进来的量，多出来就说不通，得挡住。
 */
@Getter
@Setter
public class ManifestSignoff extends BaseEntity {

    private Long id;

    /** 签收单编号，全局唯一，形如 SO-2026-0001（由仓储层分配）。 */
    private String signoffNo;

    /** 挂在哪张联单下（t_transfer_manifest.id）。 */
    private Long manifestId;

    /** 实际签收重量（千克）：认实际过磅的数。 */
    private BigDecimal receivedWeight;

    /** 实际处置重量（千克）：处置确认时落。 */
    private BigDecimal disposedWeight;

    /** 处置方式：INCINERATE 焚烧 / LANDFILL 填埋 / UTILIZE 利用 / CEMENT 水泥窑协同 / OTHER 其他。 */
    private String disposalMethod;

    /** 签收时刻。 */
    private LocalDateTime signAt;

    /** 处置确认时刻。 */
    private LocalDateTime confirmAt;

    /**
     * 处置确认：补上实际处置重量、处置方式与确认时刻。
     * 一张签收单只确认一回；处置掉的量不该多过当初签收进来的量，多出来就说不通，得挡住。
     */
    public void confirmDisposal(BigDecimal weight, String method) {
        if (this.confirmAt != null) {
            throw new BizException("该签收单已确认过处置，不能重复确认");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new BizException("实际处置重量必须大于 0");
        }
        if (this.receivedWeight != null && weight.compareTo(this.receivedWeight) > 0) {
            throw new BizException("实际处置重量不能超过实际签收重量");
        }
        this.disposedWeight = weight;
        this.disposalMethod = DisposalMethod.of(method);
        this.confirmAt = LocalDateTime.now();
    }
}
