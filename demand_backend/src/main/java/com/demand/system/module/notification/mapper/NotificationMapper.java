package com.demand.system.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 统计未读通知数量
     */
    int countUnread(@Param("userId") Long userId);

    /**
     * 查询用户最近的通知
     */
    List<Notification> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
