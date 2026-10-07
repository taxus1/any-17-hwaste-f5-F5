package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 电子转移联单仓储端口：领域层定义，基础设施层实现。
 *
 * 提交的「额度复核 + 编号分配 + 插入」要保证并发安全，由实现侧用锁兜底
 * （同一计划下两笔几乎同时提交，额度不能被一起用穿；同一个联单号只成一单）。
 * 审批 / 退回 / 启运用条件更新落库：只认源状态，后到的请求更新 0 行即拒。
 * 签收是一个事务：联单条件置签收 + 处置单位累计接收加码（许可余量硬闸）+ 签收留痕，
 * 任何一步过不去整事务回滚，同一张联单签不了两回。
 */
public interface TransferManifestRepository {

    /**
     * 提交联单：先在锁内复核额度（该计划下已开出量 + 本趟量不得盖过 approvedWeight），
     * 再分配联单编号（EM-年份-序号）并落库，状态落 SUBMITTED。
     */
    Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight);

    Mono<TransferManifest> findById(Long id);

    Mono<TransferManifest> findByManifestNo(String manifestNo);

    /** 审批：条件更新仅 SUBMITTED → APPROVED；已审过 / 退过 / 作废的更新 0 行。 */
    Mono<TransferManifest> approve(TransferManifest manifest);

    /** 退回：条件更新仅 SUBMITTED → REJECTED；已审过 / 退过 / 作废的更新 0 行。 */
    Mono<TransferManifest> reject(TransferManifest manifest);

    /** 启运：条件更新仅 APPROVED → IN_TRANSIT，记下启运时刻；其它状态更新 0 行即拒。 */
    Mono<TransferManifest> depart(TransferManifest manifest);

    /**
     * 签收：一个事务里 —— 联单条件更新仅 IN_TRANSIT → RECEIVED（记签收时刻）、
     * 处置单位累计已接收加上这趟实收（received + actualWeight 不得盖过许可上限，
     * 由条件更新原子把关）、写一笔签收留痕（实际过磅重量 + 签收时刻）。
     * 并发重复签收时后到的联单更新 0 行，整事务回滚，处置单位不会重复加码。
     */
    Mono<TransferManifest> receive(TransferManifest manifest, BigDecimal actualWeight);

    /**
     * 处置确认：已签收 → 已处置。一个事务里 —— 联单条件置 DISPOSED（只认 RECEIVED）、
     * 签收单补上实际处置重量 / 处置方式 / 确认时刻（只认还没确认过的）、
     * 这趟货占用的在库批次核销成 DISPOSED（别再当成还压在库里）。
     * 并发重复确认时后到的条件更新 0 行，整事务回滚，同一张联单确认不了两回。
     */
    Mono<TransferManifest> confirmDisposal(TransferManifest manifest, ManifestSignoff signoff);

    /** 多条件分页：计划 / 单位 / 类别 / 处置单位 / 状态均可选，一个都不传则分页列全；每行带联单号。 */
    Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                            String categoryCode, Long unitId, String status);
}
