package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * t_treatment_unit 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 * 自定义 SQL 里的 del_flag = 0 要手写（@TableLogic 只自动拼 MyBatis-Plus 生成的 SQL）。
 */
@Mapper
public interface TreatmentUnitMapper extends BaseMapper<TreatmentUnitPO> {

    /**
     * 签收加码：累计已接收 += 这趟实收。许可余量硬闸 —— 加码后不得盖过许可上限，
     * 条件不满足更新 0 行（并发两笔签收在行锁后重读最新已接收量，不会一起把许可用穿）。
     */
    @Update("UPDATE t_treatment_unit SET received_weight = received_weight + #{weight} "
            + "WHERE id = #{id} AND del_flag = 0 AND received_weight + #{weight} <= licensed_weight")
    int addReceivedWeight(@Param("id") Long id, @Param("weight") BigDecimal weight);
}
