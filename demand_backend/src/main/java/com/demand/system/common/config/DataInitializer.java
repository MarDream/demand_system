package com.demand.system.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserMapper sysUserMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper sysUserMapper, UserOrganizationMapper userOrganizationMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 检查是否已存在admin用户
        SysUser existingAdmin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin")
        );

        if (existingAdmin == null) {
            // 创建admin用户
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setRealName("系统管理员");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setStatus("active");
            sysUserMapper.insert(admin);
            log.info("Created default admin user (username: admin, password: admin)");

            // 创建组织关联
            UserOrganization org = new UserOrganization();
            org.setUserId(admin.getId());
            org.setSystemRole("admin");
            userOrganizationMapper.insert(org);
            log.info("Created admin user organization record");
        } else {
            log.info("Admin user already exists");
        }
    }
}
