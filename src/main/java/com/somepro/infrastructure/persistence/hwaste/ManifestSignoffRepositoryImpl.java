package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.model.ManifestStatus;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.ManifestSignoffPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.ManifestSignoffPO;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 签收单仓储适配器（基础设施层）。
 *
 * 签收单行本身在联单签收的同一事务里由 {@link TransferManifestRepositoryImpl} 写就（SO 取号串行），
 * 这里负责处置确认与翻单。
 *
 * 处置确认整段包在一个事务 + 一把 SO 锁里：
 * 1. 联单条件置已处置（仅 RECEIVED → DISPOSED）当闸门 —— 还没签收 / 已退回的推不动；
 *    手快点两下时第二下状态已是 DISPOSED，更新 0 行，整事务回滚，后面什么都不再动；
 * 2. 闸门推开后补签收单的处置重量、处置方式、确认时刻（处置量多于签收量已在领域里挡过一道）；
 * 3. 库存核销：把这趟货转出时盖上该联单戳的在途批次统一改成已处置 DISPOSED。
 */
@Repository
public class ManifestSignoffRepositoryImpl extends BaseBlockingRepository implements ManifestSignoffRepository {

    private final ManifestSignoffMapper manifestSignoffMapper;
    private final TransferManifestMapper transferManifestMapper;
    private final WasteStockMapper wasteStockMapper;
    private final BizNoService bizNoService;
    private final TransactionTemplate txTemplate;

    public ManifestSignoffRepositoryImpl(ManifestSignoffMapper manifestSignoffMapper,
                                         TransferManifestMapper transferManifestMapper,
                                         WasteStockMapper wasteStockMapper,
                                         BizNoService bizNoService,
                                         PlatformTransactionManager transactionManager) {
        this.manifestSignoffMapper = manifestSignoffMapper;
        this.transferManifestMapper = transferManifestMapper;
        this.wasteStockMapper = wasteStockMapper;
        this.bizNoService = bizNoService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Mono<ManifestSignoff> findByManifestId(Long manifestId) {
        return blocking(() -> {
            ManifestSignoffPO po = manifestSignoffMapper.selectOne(Wrappers.<ManifestSignoffPO>lambdaQuery()
                    .eq(ManifestSignoffPO::getManifestId, manifestId)
                    .last("LIMIT 1"));
            return po == null ? null : ManifestSignoffPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<ManifestSignoff> findById(Long id) {
        return blocking(() -> {
            ManifestSignoffPO po = manifestSignoffMapper.selectById(id);
            return po == null ? null : ManifestSignoffPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<ManifestSignoff> findBySignoffNo(String signoffNo) {
        return blocking(() -> {
            ManifestSignoffPO po = manifestSignoffMapper.selectOne(Wrappers.<ManifestSignoffPO>lambdaQuery()
                    .eq(ManifestSignoffPO::getSignoffNo, signoffNo));
            return po == null ? null : ManifestSignoffPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<ManifestSignoff> confirmDisposal(ManifestSignoff signoff) {
        return blocking(() -> bizNoService.inLock("SO", () -> txTemplate.execute(tx -> {
            // 1. 联单条件置已处置：只有 RECEIVED 推得动。未签收 / 已退回更新 0 行；
            //    同一张联单确认第二下时已是 DISPOSED，也更新 0 行 —— 整事务回滚，后到的请求没什么可动
            TransferManifestPO manifestPatch = new TransferManifestPO();
            manifestPatch.setStatus(ManifestStatus.DISPOSED.name());
            int rows = transferManifestMapper.update(manifestPatch,
                    Wrappers.<TransferManifestPO>lambdaUpdate()
                            .eq(TransferManifestPO::getId, signoff.getManifestId())
                            .eq(TransferManifestPO::getStatus, ManifestStatus.RECEIVED.name()));
            if (rows == 0) {
                throw new BizException("联单不在已签收状态，不能处置确认（未签收 / 已退回 / 已处置均不可办）");
            }
            // 2. 补签收单处置三字段。用 update wrapper 显式 set，避免 null 字段被更新策略跳过
            int signoffRows = manifestSignoffMapper.update(null,
                    Wrappers.<ManifestSignoffPO>lambdaUpdate()
                            .eq(ManifestSignoffPO::getId, signoff.getId())
                            .isNull(ManifestSignoffPO::getConfirmAt)
                            .set(ManifestSignoffPO::getDisposedWeight, signoff.getDisposedWeight())
                            .set(ManifestSignoffPO::getDisposalMethod, signoff.getDisposalMethod().name())
                            .set(ManifestSignoffPO::getConfirmAt, signoff.getConfirmAt()));
            if (signoffRows == 0) {
                // 联单闸门推开了、签收单却已确认过：理论上不该出现，并发下以事务回滚兜底
                throw new BizException("签收单已处置确认，不能重复确认");
            }
            // 3. 库存核销：这趟货转出时盖上该联单戳的在途批次统一改成已处置。
            //    条件更新只认 TRANSFERRED，重复核销时已是 DISPOSED，更新 0 行也无妨
            wasteStockMapper.disposeByManifest(signoff.getManifestId());
            return ManifestSignoffPoConverter.toDomain(manifestSignoffMapper.selectById(signoff.getId()));
        })));
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
