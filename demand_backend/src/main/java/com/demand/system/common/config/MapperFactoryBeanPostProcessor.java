package com.demand.system.common.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Spring Boot 4.0.7 + MyBatis-Plus 3.5.x 兼容补丁。
 *
 * <p>问题：MyBatis-Plus 的 {@code AutoConfiguredMapperScannerRegistrar} 在
 * {@code ImportBeanDefinitionRegistrar} 阶段注册 {@code MapperScannerConfigurer}，
 * 此时 {@code SqlSessionFactory} bean 还未注册到 BeanFactory，
 * 导致生成的 {@code MapperFactoryBean} 缺少 sqlSessionFactory 注入，
 * 调用 {@code checkDaoConfig} 时抛 {@code Property 'sqlSessionFactory' is required}。
 *
 * <p>本后置处理器在 {@code MapperFactoryBean} 初始化前（{@code afterPropertiesSet} 之前），
 * 主动从 BeanFactory 查找并注入 SqlSessionFactory。
 */
@Component
public class MapperFactoryBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(MapperFactoryBeanPostProcessor.class);

    private final BeanFactory beanFactory;

    public MapperFactoryBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MapperFactoryBean mapperFactoryBean) {
            boolean needsFactory = mapperFactoryBean.getSqlSessionFactory() == null;
            boolean needsTemplate = mapperFactoryBean.getSqlSessionTemplate() == null;
            if (needsFactory && needsTemplate) {
                if (beanFactory.containsBean("sqlSessionFactory")) {
                    SqlSessionFactory sqlSessionFactory = beanFactory.getBean("sqlSessionFactory", SqlSessionFactory.class);
                    mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
                    log.debug("Injected sqlSessionFactory into MapperFactoryBean: {}", beanName);
                } else if (beanFactory.containsBean("sqlSessionTemplate")) {
                    SqlSessionTemplate sqlSessionTemplate = beanFactory.getBean("sqlSessionTemplate", SqlSessionTemplate.class);
                    mapperFactoryBean.setSqlSessionTemplate(sqlSessionTemplate);
                    log.debug("Injected sqlSessionTemplate into MapperFactoryBean: {}", beanName);
                } else {
                    log.warn("Cannot resolve sqlSessionFactory/sqlSessionTemplate for MapperFactoryBean: {}", beanName);
                }
            }
        }
        return bean;
    }
}
