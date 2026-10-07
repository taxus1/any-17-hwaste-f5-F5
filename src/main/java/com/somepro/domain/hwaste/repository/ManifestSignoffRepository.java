package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * 签收单仓储端口：领域层定义，基础设施层实现。
 *
 * 签收单行在联单签收时由联单仓储在同一事务里写就（见 TransferManifestRepository#receive），
 * 这里只负责处置确认与查询。
 *
 * 处置确认是「一头签收单、一头联单、再连带库存」的一个事务：
 * 先以联单条件更新（仅 RECEIVED → DISPOSED）当闸门，闸门没推开（未签收 / 已退回 /
 * 已确认过）整事务回滚，签收单与库存都不会被改；推开后补签收单处置三字段，
 * 再把这趟货转出时挂上该联单的在途批次核销成已处置。
 */
public interface ManifestSignoffRepository {

    /** 按联单 id 查签收单；该联单还没签收（没有签收单行）时为空。 */
    Mono<ManifestSignoff> findByManifestId(Long manifestId);

    /** 按签收单 id 查；查不到为空。 */
    Mono<ManifestSignoff> findById(Long id);

    /** 按签收单编号查；查不到为空。 */
    Mono<ManifestSignoff> findBySignoffNo(String signoffNo);

    /**
     * 处置确认（一个事务）：
     * 1. 联单条件更新仅 RECEIVED → DISPOSED（记处置确认时刻）。还没签收 / 已退回的更新 0 行，
     *    同一张联单确认第二下时状态已是 DISPOSED 也更新 0 行 —— 后到的请求整事务回滚，没什么可动；
     * 2. 补签收单的实际处置重量、处置方式、确认时刻（领域不变量已挡处置量多于签收量）；
     * 3. 库存核销：这趟货转出时挂上该联单的批次从在途改成已处置 DISPOSED，不再当成压在库里。
     * 返回补齐后的签收单（并发重复确认时后到的请求抛业务异常）。
     */
    Mono<ManifestSignoff> confirmDisposal(ManifestSignoff signoff);

    /**
     * 多条件分页：联单 / 处置方式 / 签收日期区间均可选，一个都不传则分页列全；每行带签收单编号。
     *
     * @param signDateFrom 签收日期下界（含），按签收时刻过滤
     * @param signDateTo   签收日期上界（含），按签收时刻过滤
     */
    Mono<PageResult<ManifestSignoff>> page(int pageNum, int pageSize, Long manifestId,
                                           String disposalMethod,
                                           LocalDate signDateFrom, LocalDate signDateTo);
}
