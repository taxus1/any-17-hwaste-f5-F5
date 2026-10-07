package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 处置确认与签收单查询用例编排（应用层）。
 *
 * 货签收进厂还不算完，得在处置设施里真正处理掉、做过处置确认，这一票才算走到头：
 * 确认只认已签收的联单（还没签收、退回掉的都办不了），确认时把实际处置重量、处置方式、
 * 确认时刻补上 —— 处置掉的量不该多过当初签收进来的量，多出来就说不通，得挡住。
 * 确认完两头一起动：签收单这边补齐处置信息，联单那头从已签收推到已处置 DISPOSED，
 * 当初压在库里的那批货（manifest_id 挂着本联单的在库批次）也跟着核销，别再当成还压在库里。
 * 同一张联单别来回确认两回：领域状态机先挡一道，仓储侧条件更新（仅 RECEIVED /
 * 未确认过才生效）兜底，手快点两下第二下没什么可动。
 */
@Service
public class ManifestSignoffAppService {

    private final ManifestSignoffRepository manifestSignoffRepository;
    private final TransferManifestRepository transferManifestRepository;

    public ManifestSignoffAppService(ManifestSignoffRepository manifestSignoffRepository,
                                     TransferManifestRepository transferManifestRepository) {
        this.manifestSignoffRepository = manifestSignoffRepository;
        this.transferManifestRepository = transferManifestRepository;
    }

    /**
     * 处置确认：联单已签收 → 已处置，签收单补齐处置重量 / 方式 / 确认时刻，占用批次跟着核销。
     * 处置方式限 INCINERATE / LANDFILL / UTILIZE / CEMENT / OTHER 五种。
     */
    public Mono<ManifestSignoff> confirmDisposal(Long manifestId, String manifestNo,
                                                 BigDecimal disposedWeight, String disposalMethod) {
        if (disposedWeight == null || disposedWeight.signum() <= 0) {
            return Mono.error(new BizException("实际处置重量必须大于 0"));
        }
        return loadManifest(manifestId, manifestNo).flatMap(manifest -> {
            // 状态门槛在领域行为里：只有已签收的联单才办得了处置确认
            manifest.dispose();
            return manifestSignoffRepository.findByManifestId(manifest.getId())
                    .switchIfEmpty(Mono.error(new BizException("签收单不存在")))
                    .flatMap(signoff -> {
                        // 重量与方式校验也在领域行为里：处置量不该多过签收量，重复确认挡回
                        signoff.confirmDisposal(disposedWeight, disposalMethod);
                        return transferManifestRepository.confirmDisposal(manifest, signoff)
                                .thenReturn(signoff);
                    });
        });
    }

    /** 翻签收单：联单 / 处置方式 / 签收日期区间随意拼，一个都不填分页列全。 */
    public Mono<PageResult<ManifestSignoff>> page(int pageNum, int pageSize, Long manifestId,
                                                  String disposalMethod,
                                                  LocalDate signDateFrom, LocalDate signDateTo) {
        return manifestSignoffRepository.page(pageNum, pageSize, manifestId, disposalMethod,
                signDateFrom, signDateTo);
    }

    /** 按 id 或编号加载联单；两个都不传或查不到都视为业务失败。 */
    private Mono<TransferManifest> loadManifest(Long manifestId, String manifestNo) {
        Mono<TransferManifest> found;
        if (manifestId != null) {
            found = transferManifestRepository.findById(manifestId);
        } else if (manifestNo != null && !manifestNo.isBlank()) {
            found = transferManifestRepository.findByManifestNo(manifestNo.trim());
        } else {
            return Mono.error(new BizException("manifestId 或 manifestNo 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("转移联单不存在")));
    }
}
