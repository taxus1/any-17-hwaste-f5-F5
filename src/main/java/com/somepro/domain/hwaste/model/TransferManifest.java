package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子转移联单（聚合根，纯领域，无框架注解）。
 *
 * 一张联单一条记录：一头产废单位、一头处置单位，挂在一份已批复的年度计划下，
 * 中间这趟货归谁拉（运输单位）、走多少量（申报转移重量）都记在这张单上。
 * 联单编号形如 EM-2026-0001，由仓储层分配，全局唯一。
 *
 * 不变量集中在这里：
 * - 申报转移重量必须大于 0；新提交的一律落 SUBMITTED。
 * - 这趟量连同该计划下已开出去的联单量，加在一起不能盖过计划批复总量（额度不穿）。
 * - 审批只走一道：SUBMITTED 才能批（→ APPROVED）或退（→ REJECTED，必须写明理由）；
 *   已批过、退过、已作废的不再来回审。
 * - 启运只认已审批：APPROVED 才能启运（→ IN_TRANSIT，记下启运时刻）；还在提交、
 *   被退回、已走完的单子启不动。
 * - 签收只认在途：IN_TRANSIT 才能签收（→ RECEIVED，记下签收时刻）；签收重量认
 *   实际过磅的数，不拿申报量硬顶。处置单位的许可余量由应用层与仓储条件更新双道把关。
 */
@Getter
@Setter
public class TransferManifest extends BaseEntity {

    private Long id;

    /** 联单编号，全局唯一，形如 EM-2026-0001（由仓储层分配）。 */
    private String manifestNo;

    /** 挂在哪份年度计划下（t_transfer_plan.id）。 */
    private Long planId;

    private Long sourceId;

    /** 接收的处置单位 id（t_treatment_unit.id）。 */
    private Long unitId;

    private String categoryCode;

    /** 运输单位名称（这趟货归谁拉）。 */
    private String transporter;

    /** 这趟申报转移重量（千克）。 */
    private BigDecimal transferWeight;

    /** 是否跨省：提交时按供废 / 收货两头省份快照，1 是 / 0 否。 */
    private Integer crossProvince;

    /** 启运时刻（车出厂门那一下，启运时落）。 */
    private LocalDateTime transportBegin;

    /** 签收时刻（对方那头过磅签收那一下，签收时落）。 */
    private LocalDateTime receiveAt;

    private ManifestStatus status;

    /**
     * 新开联单：入参先过一道，落 SUBMITTED。
     * 计划是否批复、处置单位状态与接货范围、跨省限制由应用层在编排时先行校验；
     * 额度占用在仓储侧锁内复核（见 requireWithinQuota）。
     */
    public static TransferManifest create(Long planId, Long sourceId, Long unitId, String categoryCode,
                                          String transporter, BigDecimal transferWeight, boolean crossProvince) {
        if (planId == null) {
            throw new BizException("年度计划不能为空");
        }
        if (sourceId == null) {
            throw new BizException("产废单位不能为空");
        }
        if (unitId == null) {
            throw new BizException("处置单位不能为空");
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new BizException("危废类别不能为空");
        }
        if (transferWeight == null || transferWeight.signum() <= 0) {
            throw new BizException("申报转移重量必须大于 0");
        }
        TransferManifest manifest = new TransferManifest();
        manifest.setPlanId(planId);
        manifest.setSourceId(sourceId);
        manifest.setUnitId(unitId);
        manifest.setCategoryCode(categoryCode.trim());
        manifest.setTransporter(transporter == null || transporter.isBlank() ? null : transporter.trim());
        manifest.setTransferWeight(transferWeight);
        manifest.setCrossProvince(crossProvince ? 1 : 0);
        manifest.setStatus(ManifestStatus.SUBMITTED);
        return manifest;
    }

    /**
     * 额度校验：这趟要转的量 + 该计划下已开出去的联单量，不能盖过计划批复总量。
     * 由仓储侧在取号锁内调用，保证并发两笔不会一起把额度用穿。
     */
    public void requireWithinQuota(BigDecimal approvedWeight, BigDecimal usedWeight) {
        BigDecimal approved = approvedWeight == null ? BigDecimal.ZERO : approvedWeight;
        BigDecimal used = usedWeight == null ? BigDecimal.ZERO : usedWeight;
        if (used.add(transferWeight).compareTo(approved) > 0) {
            throw new BizException("转移重量超出年度计划批复额度，剩余额度不足");
        }
    }

    /** 审批通过：已提交 → 已审批。 */
    public void approve() {
        require(status == ManifestStatus.SUBMITTED, "只有已提交的联单才能审批");
        this.status = ManifestStatus.APPROVED;
    }

    /** 审批退回：已提交 → 已退回，必须写明退回理由。 */
    public void reject(String reason) {
        require(status == ManifestStatus.SUBMITTED, "只有已提交的联单才能退回");
        if (reason == null || reason.isBlank()) {
            throw new BizException("退回必须写明理由");
        }
        this.status = ManifestStatus.REJECTED;
    }

    /** 启运：已审批 → 运输中，记下启运时刻；还在提交、被退回、已走完的单子启不动。 */
    public void depart() {
        require(status == ManifestStatus.APPROVED, "只有已审批的联单才能启运");
        this.status = ManifestStatus.IN_TRANSIT;
        this.transportBegin = LocalDateTime.now();
    }

    /**
     * 签收：运输中 → 已签收，记下签收时刻。
     * 签收重量认实际过磅的那个数（actualWeight），跟申报量对不齐也照实收落账；
     * 货还没出门（未启运）或已走完的单子签不了。
     */
    public void receive(BigDecimal actualWeight) {
        require(status == ManifestStatus.IN_TRANSIT, "只有运输中的联单才能签收");
        if (actualWeight == null || actualWeight.signum() <= 0) {
            throw new BizException("签收重量必须大于 0");
        }
        this.status = ManifestStatus.RECEIVED;
        this.receiveAt = LocalDateTime.now();
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BizException(message);
        }
    }
}
