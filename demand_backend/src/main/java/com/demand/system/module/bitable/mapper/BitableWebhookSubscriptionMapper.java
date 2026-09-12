package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableWebhookSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 多维表格-Webhook 订阅 Mapper
 */
@Mapper
public interface BitableWebhookSubscriptionMapper extends BaseMapper<BitableWebhookSubscription> {
}
