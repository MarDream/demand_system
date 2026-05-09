package com.demand.system.module.columnconfig.service;

import java.util.List;

public interface UserColumnConfigService {

    List<String> getVisibleColumns(Long userId, String pageKey);

    void saveVisibleColumns(Long userId, String pageKey, List<String> columns);
}