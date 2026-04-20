package com.demand.system.common.constant;

public interface RedisConstants {

    String TOKEN_PREFIX = "auth:token:";
    String USER_PREFIX = "auth:user:";
    String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    String WORKFLOW_PREFIX = "workflow:";
    String LOCK_PREFIX = "lock:";
    long TOKEN_EXPIRE_SECONDS = 7200;
}
