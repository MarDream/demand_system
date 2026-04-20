package com.demand.system.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    @Select("SELECT * FROM notifications WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Notification> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
