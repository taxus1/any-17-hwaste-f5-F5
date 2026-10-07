package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 签收单仓储端口：领域层定义，基础设施层实现。
 */
public interface ManifestSignoffRepository {

    /** 按联单找签收单：一张联单签收时落一行，找不到则空。 */
    Mono<ManifestSignoff> findByManifestId(Long manifestId);

    /** 多条件分页查签收单：联单 / 处置方式 / 签收日期区间均可选，一个都不填分页列全。 */
    Mono<PageResult<ManifestSignoff>> page(int pageNum, int pageSize, Long manifestId, String disposalMethod,
                                           LocalDate signDateFrom, LocalDate signDateTo);
}
