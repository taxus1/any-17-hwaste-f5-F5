package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处置利用单位（纯领域对象）。开联单只关心它状态是否正常、能接哪些类别、
 * 落在哪个省；签收时还要盯住它的许可余量 —— 累计已接收加上这趟实收，
 * 不该盖过许可上限，别让一家厂接爆了。
 */
@Getter
@Setter
public class TreatmentUnit {

    private Long id;

    /** 处置单位编号，形如 TU-2026-0001。 */
    private String unitNo;

    private String name;

    /** 所在省份（跨省判定用）。 */
    private String province;

    /** 许可经营重量上限（千克）。 */
    private BigDecimal licensedWeight;

    /** 累计已接收重量（千克，签收一笔往上加一笔）。 */
    private BigDecimal receivedWeight;

    /** 可处置类别代码，逗号分隔（如 HW08,HW09）。 */
    private String disposes;

    private UnitStatus status;

    /** 只有正常（ACTIVE）的单位才接得了新货；停用、吊销都挡回去。 */
    public boolean isActive() {
        return this.status == UnitStatus.ACTIVE;
    }

    /** 这趟货的类别得落在对方能接的范围里。 */
    public boolean canAccept(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()
                || disposes == null || disposes.isBlank()) {
            return false;
        }
        for (String code : disposes.split(",")) {
            if (categoryCode.trim().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 许可余量把关：累计已接收 + 这趟实收不得盖过许可上限，超了就不许签收，
     * 先把额度腾出来再说。这是应用层的先行校验；并发下的硬闸在仓储侧条件更新。
     */
    public void requireLicenseHeadroom(BigDecimal incomingWeight) {
        BigDecimal incoming = incomingWeight == null ? BigDecimal.ZERO : incomingWeight;
        BigDecimal licensed = licensedWeight == null ? BigDecimal.ZERO : licensedWeight;
        BigDecimal received = receivedWeight == null ? BigDecimal.ZERO : receivedWeight;
        if (received.add(incoming).compareTo(licensed) > 0) {
            throw new BizException("处置单位许可余量不足，无法签收");
        }
    }
}
