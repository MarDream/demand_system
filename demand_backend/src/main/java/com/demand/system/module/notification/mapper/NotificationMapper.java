package com.demand.system.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    int countUnread(@Param("userId") Long userId);

    List<Notification> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
