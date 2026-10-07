package com.somepro.interfaces.rest.hwaste.converter;

import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.hwaste.vo.ManifestSignoffVO;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ManifestSignoff（领域）→ 对外 VO 转换器（用户接口层）。
 */
public final class ManifestSignoffVoConverter {

    private ManifestSignoffVoConverter() {
    }

    public static ManifestSignoffVO toVo(ManifestSignoff domain) {
        return new ManifestSignoffVO(
                domain.getId(),
                domain.getSignoffNo(),
                domain.getManifestId(),
                domain.getReceivedWeight(),
                domain.getDisposedWeight(),
                domain.getDisposalMethod() == null ? null : domain.getDisposalMethod().name(),
                domain.getSignAt(),
                domain.getConfirmAt(),
                domain.getCreateTime());
    }

    public static PageVO<ManifestSignoffVO> toPageVo(PageResult<ManifestSignoff> page) {
        List<ManifestSignoffVO> content = page.content().stream()
                .map(ManifestSignoffVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
