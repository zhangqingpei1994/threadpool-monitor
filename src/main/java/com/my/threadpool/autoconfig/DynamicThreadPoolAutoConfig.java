package com.my.threadpool.autoconfig;

import com.my.threadpool.ThreadPoolHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.my.threadpool.config.DiamondConfig;
import com.my.threadpool.config.ThreadPoolProperties;
import com.my.threadpool.decorator.ContextCopyingDecorator;
import com.my.threadpool.handler.MonitorRejectedHandler;
import com.my.threadpool.monitor.DynamicThreadPoolMonitor;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 动态线程池自动配置
 * @author zhangqingpei
 */
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnClass({ThreadPoolExecutor.class, ThreadPoolTaskExecutor.class})
public class DynamicThreadPoolAutoConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicThreadPoolAutoConfig.class);

    @Autowired
    private ThreadPoolProperties threadPoolProperties;

    @Autowired
    private DynamicThreadPoolMonitor dynamicThreadPoolMonitor;


    @Autowired
    private ApplicationContext applicationContext;



    /**
     * 创建默认的动态线程池
     */
    @Bean(name = "dynamicTaskExecutor")
    @ConditionalOnMissingBean(name = "dynamicTaskExecutor")
    public ThreadPoolTaskExecutor dynamicTaskExecutor(DiamondConfig diamondConfig) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 设置基础参数
        executor.setCorePoolSize(threadPoolProperties.getCorePoolSize());
        executor.setMaxPoolSize(threadPoolProperties.getMaxPoolSize());
        executor.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        executor.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        executor.setThreadNamePrefix(threadPoolProperties.getThreadNamePrefix());
        executor.setAllowCoreThreadTimeOut(threadPoolProperties.isAllowCoreThreadTimeOut());

        // 设置上下文传递装饰器
        executor.setTaskDecorator(new ContextCopyingDecorator());

        // 设置拒绝策略
        MonitorRejectedHandler rejectedHandler = new MonitorRejectedHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setRejectedExecutionHandler(rejectedHandler);

        executor.initialize();

        // 线程预热：提前启动所有核心线程
        executor.getThreadPoolExecutor().prestartAllCoreThreads();


        // 注册到监控器
        dynamicThreadPoolMonitor.register("dynamicTaskExecutor", executor);

        // 注册到Diamond配置并订阅变更
        DiamondConfig.DynamicThreadPoolConfig config = new DiamondConfig.DynamicThreadPoolConfig();
        config.setCorePoolSize(threadPoolProperties.getCorePoolSize());
        config.setMaxPoolSize(threadPoolProperties.getMaxPoolSize());
        config.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        config.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        diamondConfig.registerConfig("dynamicTaskExecutor", config);

        // 设置配置变更监听器
        setupConfigChangeListener(diamondConfig);

        return executor;
    }

    /**
     * 设置Diamond配置变更监听器
     */
    private void setupConfigChangeListener(DiamondConfig diamondConfig) {
        diamondConfig.setConfigChangeListener((poolName, config) -> {
            ThreadPoolExecutor executor = ThreadPoolHolder.get(poolName);
            if (executor != null ) {
                LOGGER.info("线程池[{}]动态配置变更, 正在更新参数: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                        poolName, config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
                
                executor.setCorePoolSize(config.getCorePoolSize());
                executor.setMaximumPoolSize(config.getMaxPoolSize());
                executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);
            }
        });
    }

    /**
     * 自动注册Spring容器中已有的ThreadPoolTaskExecutor
     */
    @PostConstruct
    public void autoRegisterExistingExecutors() {
        Map<String, ThreadPoolTaskExecutor> taskExecutors = getTaskExecutors();
        if (taskExecutors != null && !taskExecutors.isEmpty()) {
            taskExecutors.forEach((name, executor) -> {
                if (!"dynamicTaskExecutor".equals(name)) {
                    autoDecorateExecutor(name, executor);
                }
            });
        }
    }

    /**
     * 获取所有ThreadPoolTaskExecutor Bean
     */
    private Map<String, ThreadPoolTaskExecutor> getTaskExecutors() {
        try {
            return applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 自动装饰已有线程池
     */
    private void autoDecorateExecutor(String name, ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor originalPool = executor.getThreadPoolExecutor();
        if (originalPool == null) {
            return;
        }

        // 1. 注入上下文传递装饰器
        executor.setTaskDecorator(new ContextCopyingDecorator());

        // 2. 包装拒绝策略
        MonitorRejectedHandler handler = new MonitorRejectedHandler(
                originalPool.getRejectedExecutionHandler());
        handler.setPoolName(name);
        originalPool.setRejectedExecutionHandler(handler);

        // 3. 注册到监控管理器
        dynamicThreadPoolMonitor.register(name, executor);

    }
}