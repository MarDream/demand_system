package com.demand.system.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MybatisPlusProperties.class)
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * 显式声明 SqlSessionFactory bean。
     *
     * <p>Spring Boot 4.0.7 把 DataSourceAutoConfiguration 迁到新包
     * {@code org.springframework.boot.jdbc.autoconfigure}，mybatis-plus 3.5.7
     * 内的 {@code @AutoConfigureAfter} 仍引用旧包路径
     * {@code org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration}，
     * 类全限定名不匹配，Spring 静默忽略，导致 MybatisPlusAutoConfiguration 评估
     * {@code @ConditionalOnSingleCandidate(DataSource)} 时 DataSource bean 尚未注册，
     * 整个 auto-config 被丢弃。
     *
     * <p>本 bean 方法以 {@code DataSource} 为参数，Spring 容器会强制等到 DataSource
     * 就绪后调用，从而保证 SqlSessionFactory 在 DataSource 之后注册。
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               MybatisPlusProperties properties,
                                               MybatisPlusInterceptor mybatisPlusInterceptor,
                                               ObjectProvider<GlobalConfig> globalConfigProvider) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        factoryBean.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        factoryBean.setMapperLocations(resolveMapperLocations(properties));
        // 3.5.7 的 MybatisPlusProperties.CoreConfiguration.applyTo() 调用了
        // PropertyMapper.alwaysApplyingWhenNonNull()，但 Spring Boot 4.0.7 已删除该方法。
        // 这里手写字段复制，避开不兼容 API。
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        if (properties.getConfiguration() != null) {
            applyCoreConfiguration(properties.getConfiguration(), mybatisConfiguration);
        }
        if (properties.getConfigurationProperties() != null) {
            mybatisConfiguration.setVariables(properties.getConfigurationProperties());
        }
        factoryBean.setConfiguration(mybatisConfiguration);
        GlobalConfig globalConfig = globalConfigProvider.getIfAvailable();
        if (globalConfig != null) {
            factoryBean.setGlobalConfig(globalConfig);
        }
        factoryBean.setPlugins(mybatisPlusInterceptor);
        return factoryBean.getObject();
    }

    private Resource[] resolveMapperLocations(MybatisPlusProperties properties) throws Exception {
        String[] locations = properties.getMapperLocations();
        if (locations == null || locations.length == 0) {
            return new Resource[0];
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        java.util.List<Resource> resources = new java.util.ArrayList<>();
        for (String location : locations) {
            for (Resource resource : resolver.getResources(location)) {
                resources.add(resource);
            }
        }
        return resources.toArray(new Resource[0]);
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 复制 mybatis-plus 3.5.7 MybatisPlusProperties.CoreConfiguration.applyTo(MybatisConfiguration)
     * 行为，但绕开 Spring Boot 4.0.7 已删除的 PropertyMapper.alwaysApplyingWhenNonNull() 方法。
     */
    private static void applyCoreConfiguration(MybatisPlusProperties.CoreConfiguration src,
                                              MybatisConfiguration target) {
        if (src.getSafeRowBoundsEnabled() != null) target.setSafeRowBoundsEnabled(src.getSafeRowBoundsEnabled());
        if (src.getSafeResultHandlerEnabled() != null) target.setSafeResultHandlerEnabled(src.getSafeResultHandlerEnabled());
        if (src.getMapUnderscoreToCamelCase() != null) target.setMapUnderscoreToCamelCase(src.getMapUnderscoreToCamelCase());
        if (src.getAggressiveLazyLoading() != null) target.setAggressiveLazyLoading(src.getAggressiveLazyLoading());
        if (src.getMultipleResultSetsEnabled() != null) target.setMultipleResultSetsEnabled(src.getMultipleResultSetsEnabled());
        if (src.getUseGeneratedKeys() != null) target.setUseGeneratedKeys(src.getUseGeneratedKeys());
        if (src.getUseColumnLabel() != null) target.setUseColumnLabel(src.getUseColumnLabel());
        if (src.getCacheEnabled() != null) target.setCacheEnabled(src.getCacheEnabled());
        if (src.getCallSettersOnNulls() != null) target.setCallSettersOnNulls(src.getCallSettersOnNulls());
        if (src.getUseActualParamName() != null) target.setUseActualParamName(src.getUseActualParamName());
        if (src.getReturnInstanceForEmptyRow() != null) target.setReturnInstanceForEmptyRow(src.getReturnInstanceForEmptyRow());
        if (src.getShrinkWhitespacesInSql() != null) target.setShrinkWhitespacesInSql(src.getShrinkWhitespacesInSql());
        if (src.getNullableOnForEach() != null) target.setNullableOnForEach(src.getNullableOnForEach());
        if (src.getArgNameBasedConstructorAutoMapping() != null) target.setArgNameBasedConstructorAutoMapping(src.getArgNameBasedConstructorAutoMapping());
        if (src.getLazyLoadingEnabled() != null) target.setLazyLoadingEnabled(src.getLazyLoadingEnabled());
        if (src.getDefaultStatementTimeout() != null) target.setDefaultStatementTimeout(src.getDefaultStatementTimeout());
        if (src.getDefaultFetchSize() != null) target.setDefaultFetchSize(src.getDefaultFetchSize());
        if (src.getLocalCacheScope() != null) target.setLocalCacheScope(src.getLocalCacheScope());
        if (src.getJdbcTypeForNull() != null) target.setJdbcTypeForNull(src.getJdbcTypeForNull());
        if (src.getDefaultResultSetType() != null) target.setDefaultResultSetType(src.getDefaultResultSetType());
        if (src.getDefaultExecutorType() != null) target.setDefaultExecutorType(src.getDefaultExecutorType());
        if (src.getAutoMappingBehavior() != null) target.setAutoMappingBehavior(src.getAutoMappingBehavior());
        if (src.getAutoMappingUnknownColumnBehavior() != null) target.setAutoMappingUnknownColumnBehavior(src.getAutoMappingUnknownColumnBehavior());
        if (src.getLogPrefix() != null) target.setLogPrefix(src.getLogPrefix());
        if (src.getLazyLoadTriggerMethods() != null) target.setLazyLoadTriggerMethods(src.getLazyLoadTriggerMethods());
        Class<? extends org.apache.ibatis.logging.Log> logImpl = src.getLogImpl();
        if (logImpl != null) target.setLogImpl(logImpl);
        Class<? extends VFS> vfsImpl = src.getVfsImpl();
        if (vfsImpl != null) target.setVfsImpl(vfsImpl);
        if (src.getDefaultSqlProviderType() != null) target.setDefaultSqlProviderType(src.getDefaultSqlProviderType());
        Class<?> defaultEnumTypeHandler = src.getDefaultEnumTypeHandler();
        if (defaultEnumTypeHandler != null) {
            @SuppressWarnings("unchecked")
            Class<? extends TypeHandler> handler = (Class<? extends TypeHandler>) defaultEnumTypeHandler;
            target.setDefaultEnumTypeHandler(handler);
        }
        if (src.getConfigurationFactory() != null) target.setConfigurationFactory(src.getConfigurationFactory());
        if (src.getVariables() != null) target.setVariables(src.getVariables());
        if (src.getDatabaseId() != null) target.setDatabaseId(src.getDatabaseId());
        Class<? extends LanguageDriver> lang = src.getDefaultScriptingLanguageDriver();
        if (lang != null) target.setDefaultScriptingLanguage(lang);
        if (src.getUseGeneratedShortKey() != null) target.setUseGeneratedShortKey(src.getUseGeneratedShortKey());
    }
}
