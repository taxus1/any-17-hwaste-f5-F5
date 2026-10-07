package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签收单（聚合根，纯领域，无框架注解）。
 *
 * 一张联单一条签收单：货拉到处置设施门口过磅签收时立单，编号形如 SO-2026-0001，
 * 由仓储层分配，全局唯一。单上先记实际签收重量与签收时刻；货在设施里真正处理掉后
 * 再做处置确认，补上实际处置重量、处置方式与确认时刻。
 *
 * 不变量集中在这里：
 * - 签收重量必须大于 0，认实际过磅的数，不拿联单申报量硬顶。
 * - 处置确认只能办一回：处置方式 / 处置重量 / 确认时刻落上后不得再动，
 *   手快点两下第二下没什么可改（仓储侧再以联单条件更新兜底）。
 * - 处置重量必须大于 0，且不得多过当初签收进来的量 —— 处理掉的比签收进来的还多，说不通。
 */
@Getter
@Setter
public class ManifestSignoff extends BaseEntity {

    private Long id;

    /** 签收单编号，全局唯一，形如 SO-2026-0001（由仓储层分配）。 */
    private String signoffNo;

    /** 挂在哪张联单下（t_transfer_manifest.id），一张联单一条签收单。 */
    private Long manifestId;

    /** 实际签收重量（千克）：门口过磅的数。 */
    private BigDecimal receivedWeight;

    /** 实际处置重量（千克）：处置确认时落，不得多过签收重量。 */
    private BigDecimal disposedWeight;

    /** 处置方式：处置确认时落。 */
    private DisposalMethod disposalMethod;

    /** 签收时刻（门口签收那一下，立单时落）。 */
    private LocalDateTime signAt;

    /** 处置确认时刻（真正处理掉那一下，确认时落）。 */
    private LocalDateTime confirmAt;

    /**
     * 立签收单：联单签收时调用，记实际过磅重量与签收时刻。
     * 处置三字段（处置重量 / 方式 / 确认时刻）先空着，等处置确认再补。
     */
    public static ManifestSignoff sign(Long manifestId, BigDecimal receivedWeight, LocalDateTime signAt) {
        if (manifestId == null) {
            throw new BizException("联单不能为空");
        }
        if (receivedWeight == null || receivedWeight.signum() <= 0) {
            throw new BizException("签收重量必须大于 0");
        }
        ManifestSignoff signoff = new ManifestSignoff();
        signoff.setManifestId(manifestId);
        signoff.setReceivedWeight(receivedWeight);
        signoff.setSignAt(signAt);
        return signoff;
    }

    /**
     * 处置确认：补上实际处置重量、处置方式与确认时刻。
     * 已经确认过的不再来回确认；处置重量必须大于 0 且不得多过签收进来的量。
     */
    public void confirm(BigDecimal disposedWeight, DisposalMethod method) {
        if (this.confirmAt != null) {
            // 同一张联单别来回确认两回：第二下没什么可动
            throw new BizException("该签收单已处置确认，不能重复确认");
        }
        if (disposedWeight == null || disposedWeight.signum() <= 0) {
            throw new BizException("处置重量必须大于 0");
        }
        if (disposedWeight.compareTo(this.receivedWeight) > 0) {
            throw new BizException("实际处置重量不得多过实际签收重量");
        }
        if (method == null) {
            throw new BizException("处置方式不能为空");
        }
        this.disposedWeight = disposedWeight;
        this.disposalMethod = method;
        this.confirmAt = LocalDateTime.now();
    }

    /** 是否已做过处置确认。 */
    public boolean isConfirmed() {
        return this.confirmAt != null;
    }
}
