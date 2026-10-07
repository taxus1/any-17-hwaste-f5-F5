package com.somepro.interfaces.rest.hwaste;

import com.somepro.application.hwaste.ManifestSignoffAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hwaste.converter.ManifestSignoffVoConverter;
import com.somepro.interfaces.rest.hwaste.vo.ManifestSignoffVO;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 签收单接口（用户接口层）：处置确认、详情与多条件翻页。
 *
 * 签收单在联单签收（/api/hwaste/manifest/receive）时随单一事立好，这里不另开立单入口。
 * 处置确认的签收单定位参数：signoffId 或 signoffNo 传其一即可。
 */
@RestController
@RequestMapping("/api/hwaste/signoff")
public class ManifestSignoffController {

    private final ManifestSignoffAppService manifestSignoffAppService;

    public ManifestSignoffController(ManifestSignoffAppService manifestSignoffAppService) {
        this.manifestSignoffAppService = manifestSignoffAppService;
    }

    /**
     * 处置确认：只有已签收联单的签收单办得了（未签收 / 已退回 / 已处置均办不了）。
     * disposedWeight 为实际处置重量，不得多过实际签收重量；disposalMethod 取
     * INCINERATE / LANDFILL / UTILIZE / CEMENT / OTHER。确认后联单推到已处置，挂该联单的批次跟着核销。
     */
    @PostMapping("/confirm")
    public Mono<Result<ManifestSignoffVO>> confirm(@RequestParam(required = false) Long signoffId,
                                                   @RequestParam(required = false) String signoffNo,
                                                   @RequestParam(required = false) BigDecimal disposedWeight,
                                                   @RequestParam(required = false) String disposalMethod) {
        return manifestSignoffAppService.confirm(signoffId, signoffNo, disposedWeight, disposalMethod)
                .map(ManifestSignoffVoConverter::toVo)
                .map(Result::ok);
    }

    /** 签收单详情：signoffId 或 signoffNo 传其一。 */
    @GetMapping("/detail")
    public Mono<Result<ManifestSignoffVO>> detail(@RequestParam(required = false) Long signoffId,
                                                  @RequestParam(required = false) String signoffNo) {
        return manifestSignoffAppService.detail(signoffId, signoffNo)
                .map(ManifestSignoffVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 翻签收单：联单 / 处置方式 / 签收日期区间随意拼，一个都不填分页列全；每行带签收单编号。
     * signDateFrom / signDateTo 形如 2026-10-07，区间两端都含当天。
     */
    @GetMapping("/page")
    public Mono<Result<PageVO<ManifestSignoffVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long manifestId,
            @RequestParam(required = false) String disposalMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signDateTo) {
        return manifestSignoffAppService
                .page(pageNum, pageSize, manifestId, disposalMethod, signDateFrom, signDateTo)
                .map(ManifestSignoffVoConverter::toPageVo)
                .map(Result::ok);
    }
}
