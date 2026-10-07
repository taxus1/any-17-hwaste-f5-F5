package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.ManifestSignoffPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.ManifestSignoffPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 签收单仓储适配器（基础设施层）。
 *
 * 处置确认的「联单置已处置 + 签收单补齐 + 库存核销」事务在 TransferManifestRepositoryImpl 里
 * （与签收时写签收留痕同一处），这里只承担签收单的查询。
 */
@Repository
public class ManifestSignoffRepositoryImpl extends BaseBlockingRepository implements ManifestSignoffRepository {

    private final ManifestSignoffMapper manifestSignoffMapper;

    public ManifestSignoffRepositoryImpl(ManifestSignoffMapper manifestSignoffMapper) {
        this.manifestSignoffMapper = manifestSignoffMapper;
    }

    @Override
    public Mono<ManifestSignoff> findByManifestId(Long manifestId) {
        return blocking(() -> {
            // 一张联单签收时落一行；防御性取最新一行，脏数据下也不至于直接炸出来
            ManifestSignoffPO po = manifestSignoffMapper.selectOne(Wrappers.<ManifestSignoffPO>lambdaQuery()
                    .eq(ManifestSignoffPO::getManifestId, manifestId)
                    .orderByDesc(ManifestSignoffPO::getId)
                    .last("LIMIT 1"));
            return po == null ? null : ManifestSignoffPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<ManifestSignoff>> page(int pageNum, int pageSize, Long manifestId,
                                                  String disposalMethod,
                                                  LocalDate signDateFrom, LocalDate signDateTo) {
        return this.<PageResult<ManifestSignoff>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<ManifestSignoffPO> wrapper = Wrappers.<ManifestSignoffPO>lambdaQuery()
                        .eq(manifestId != null, ManifestSignoffPO::getManifestId, manifestId)
                        .eq(disposalMethod != null && !disposalMethod.isBlank(),
                                ManifestSignoffPO::getDisposalMethod, disposalMethod)
                        .ge(signDateFrom != null, ManifestSignoffPO::getSignAt,
                                signDateFrom == null ? null : signDateFrom.atStartOfDay())
                        .lt(signDateTo != null, ManifestSignoffPO::getSignAt,
                                signDateTo == null ? null : signDateTo.plusDays(1).atStartOfDay())
                        .orderByDesc(ManifestSignoffPO::getId);
                List<ManifestSignoffPO> rows = manifestSignoffMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<ManifestSignoff> content = rows.stream()
                        .map(ManifestSignoffPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
