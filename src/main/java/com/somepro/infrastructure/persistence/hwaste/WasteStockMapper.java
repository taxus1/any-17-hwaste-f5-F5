package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.WasteStockPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * t_waste_stock 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC）。
 *
 * 自定义 SQL 里的 del_flag = 0 要手写（@TableLogic 只自动拼 MyBatis-Plus 生成的 SQL）。
 */
@Mapper
public interface WasteStockMapper extends BaseMapper<WasteStockPO> {

    /** 该单位该类别在库（IN_STOCK）批次重量合计；没有记录时返回 0。 */
    @Select("SELECT IFNULL(SUM(weight_kg), 0) FROM t_waste_stock "
            + "WHERE del_flag = 0 AND source_id = #{sourceId} AND category_code = #{categoryCode} "
            + "AND status = 'IN_STOCK'")
    BigDecimal sumInStockWeight(@Param("sourceId") Long sourceId, @Param("categoryCode") String categoryCode);

    @Select("SELECT MAX(batch_no) FROM t_waste_stock WHERE batch_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String maxBatchNo(@Param("prefix") String prefix);

    /**
     * 库存核销：把指定联单转出时占用的批次从「已转出」核销成「已处置」。
     * 只认 TRANSFERRED —— 重复核销时已是 DISPOSED，更新 0 行，天然幂等；
     * VOID 是拆分后退出在库账的父批（货在子批上），不在核销范围。
     */
    @Update("UPDATE t_waste_stock SET status = 'DISPOSED' "
            + "WHERE del_flag = 0 AND manifest_id = #{manifestId} AND status = 'TRANSFERRED'")
    int disposeByManifest(@Param("manifestId") Long manifestId);
}
