package com.demand.system.module.user.service;

import com.demand.system.module.user.entity.Department;
import com.demand.system.module.user.entity.Position;
import com.demand.system.module.user.entity.Region;

import java.util.List;

public interface OrganizationService {

    List<Region> getRegionTree();

    List<Department> getDepartmentTree();

    List<Position> listPositions();

    void createRegion(Region region);

    void updateRegion(Region region);

    void createDepartment(Department dept);

    void updateDepartment(Department dept);

    void createPosition(Position position);

    void updatePosition(Position position);

    void updateOrg(Long userId, Long regionId, Long departmentId, Long positionId, String systemRole);

    void deleteRegion(Long id);
    void deleteDepartment(Long id);
    void deletePosition(Long id);
}
