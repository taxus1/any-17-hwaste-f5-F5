package com.somepro.infrastructure.persistence.hwaste.converter;

import com.somepro.domain.hwaste.model.DisposalMethod;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.infrastructure.persistence.hwaste.po.ManifestSignoffPO;

/**
 * ManifestSignoffPO（表）↔ ManifestSignoff（领域）转换器（基础设施层）。
 * 处置方式在库里存字符串，在领域里是枚举，互转在这里收口。
 */
public final class ManifestSignoffPoConverter {

    private ManifestSignoffPoConverter() {
    }

    public static ManifestSignoffPO toPo(ManifestSignoff domain) {
        ManifestSignoffPO po = new ManifestSignoffPO();
        po.setId(domain.getId());
        po.setSignoffNo(domain.getSignoffNo());
        po.setManifestId(domain.getManifestId());
        po.setReceivedWeight(domain.getReceivedWeight());
        po.setDisposedWeight(domain.getDisposedWeight());
        po.setDisposalMethod(domain.getDisposalMethod() == null ? null : domain.getDisposalMethod().name());
        po.setSignAt(domain.getSignAt());
        po.setConfirmAt(domain.getConfirmAt());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static ManifestSignoff toDomain(ManifestSignoffPO po) {
        ManifestSignoff domain = new ManifestSignoff();
        domain.setId(po.getId());
        domain.setSignoffNo(po.getSignoffNo());
        domain.setManifestId(po.getManifestId());
        domain.setReceivedWeight(po.getReceivedWeight());
        domain.setDisposedWeight(po.getDisposedWeight());
        domain.setDisposalMethod(po.getDisposalMethod() == null ? null : DisposalMethod.valueOf(po.getDisposalMethod()));
        domain.setSignAt(po.getSignAt());
        domain.setConfirmAt(po.getConfirmAt());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
