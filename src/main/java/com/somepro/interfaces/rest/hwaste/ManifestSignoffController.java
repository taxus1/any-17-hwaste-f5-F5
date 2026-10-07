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
 * 签收与处置确认接口（用户接口层）：处置确认 + 签收单多条件翻页。
 *
 * 货签收进厂还不算完，在处置设施里真正处理掉、做过处置确认，这一票才算走到头：
 * 确认只认已签收的联单，处置重量不该多过当初签收进来的量；确认完签收单补齐处置信息、
 * 联单推到已处置 DISPOSED，当初压在库里的那批货也跟着核销。
 */
@RestController
@RequestMapping("/api/hwaste/signoff")
public class ManifestSignoffController {

    private final ManifestSignoffAppService manifestSignoffAppService;

    public ManifestSignoffController(ManifestSignoffAppService manifestSignoffAppService) {
        this.manifestSignoffAppService = manifestSignoffAppService;
    }

    /**
     * 处置确认：manifestId 或 manifestNo 传其一；只有已签收的联单办得了，
     * 还没签收、退回掉的都办不了；同一张联单确认不了两回。
     * disposedWeight 认实际处置掉的量（不该多过签收量）；
     * disposalMethod 限 INCINERATE / LANDFILL / UTILIZE / CEMENT / OTHER 五种。
     */
    @PostMapping("/confirm")
    public Mono<Result<ManifestSignoffVO>> confirm(@RequestParam(required = false) Long manifestId,
                                                   @RequestParam(required = false) String manifestNo,
                                                   @RequestParam(required = false) BigDecimal disposedWeight,
                                                   @RequestParam(required = false) String disposalMethod) {
        return manifestSignoffAppService.confirmDisposal(manifestId, manifestNo, disposedWeight, disposalMethod)
                .map(ManifestSignoffVoConverter::toVo)
                .map(Result::ok);
    }

    /** 翻签收单：联单 / 处置方式 / 签收日期区间随意拼，都不传则分页列全；每行带签收单编号。 */
    @GetMapping("/page")
    public Mono<Result<PageVO<ManifestSignoffVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long manifestId,
            @RequestParam(required = false) String disposalMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signDateTo) {
        return manifestSignoffAppService.page(pageNum, pageSize, manifestId, disposalMethod,
                        signDateFrom, signDateTo)
                .map(ManifestSignoffVoConverter::toPageVo)
                .map(Result::ok);
    }
}
