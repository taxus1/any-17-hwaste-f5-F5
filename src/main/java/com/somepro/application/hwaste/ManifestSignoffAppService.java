package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.DisposalMethod;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 处置确认与签收单查询用例编排（应用层）。
 *
 * 货签收进厂不算完，得在处置设施里真正处理掉：处置确认只对已签收（RECEIVED）的联单办，
 * 还没签收、退回掉的都办不了；实际处置量不得多过当初签收进来的量。确认一办三头一起动 ——
 * 签收单补处置重量 / 处置方式 / 确认时刻，联单从已签收推到已处置 DISPOSED，
 * 当初从库里挪出来挂在这张联单上的批次跟着核销成已处置。同一张联单确认第二下没什么可动。
 *
 * 翻签收单：联单 / 处置方式 / 签收日期区间随意拼，一个都不填分页列全，每行带签收单编号。
 */
@Service
public class ManifestSignoffAppService {

    private final ManifestSignoffRepository manifestSignoffRepository;

    public ManifestSignoffAppService(ManifestSignoffRepository manifestSignoffRepository) {
        this.manifestSignoffRepository = manifestSignoffRepository;
    }

    /**
     * 处置确认：按签收单 id 或编号定位（传其一），补实际处置重量与处置方式，确认时刻由系统落。
     * 未签收 / 已退回的联单办不了；处置量多于签收量、重复确认都挡回去（仓储事务再兜底一道）。
     */
    public Mono<ManifestSignoff> confirm(Long signoffId, String signoffNo,
                                         BigDecimal disposedWeight, String disposalMethod) {
        if (disposedWeight == null || disposedWeight.signum() <= 0) {
            return Mono.error(new BizException("处置重量必须大于 0"));
        }
        DisposalMethod method;
        try {
            method = DisposalMethod.of(disposalMethod);
        } catch (BizException e) {
            return Mono.error(e);
        }
        return load(signoffId, signoffNo).flatMap(signoff -> {
            // 领域不变量：处置量不得多过签收量、同一张单不重复确认
            signoff.confirm(disposedWeight, method);
            return manifestSignoffRepository.confirmDisposal(signoff);
        });
    }

    /** 签收单详情：按 id 或编号查。 */
    public Mono<ManifestSignoff> detail(Long signoffId, String signoffNo) {
        return load(signoffId, signoffNo);
    }

    /** 翻签收单：联单 / 处置方式 / 签收日期区间均可选，啥都不挑分页列全。 */
    public Mono<PageResult<ManifestSignoff>> page(int pageNum, int pageSize, Long manifestId,
                                                  String disposalMethod,
                                                  LocalDate signDateFrom, LocalDate signDateTo) {
        String method = disposalMethod == null || disposalMethod.isBlank()
                ? null : DisposalMethod.of(disposalMethod).name();
        return manifestSignoffRepository.page(pageNum, pageSize, manifestId, method, signDateFrom, signDateTo);
    }

    /** 按 id 或编号加载签收单；两个都不传或查不到都视为业务失败。 */
    private Mono<ManifestSignoff> load(Long signoffId, String signoffNo) {
        Mono<ManifestSignoff> found;
        if (signoffId != null) {
            found = manifestSignoffRepository.findById(signoffId);
        } else if (signoffNo != null && !signoffNo.isBlank()) {
            found = manifestSignoffRepository.findBySignoffNo(signoffNo.trim());
        } else {
            return Mono.error(new BizException("signoffId 或 signoffNo 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("签收单不存在")));
    }
}
