package com.my.threadpool.autoconfig;

import com.my.threadpool.ThreadPoolHolder;
import com.my.threadpool.ThreadPoolFactory;
import com.my.threadpool.autoconfig.DiamondConfig;
import com.my.threadpool.autoconfig.ThreadPoolProperties;
import com.my.threadpool.decorator.ContextCopyingDecorator;
import com.my.threadpool.handler.MonitorRejectedHandler;
import com.my.threadpool.monitor.DynamicThreadPoolMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
     * 创建线程池工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolFactory threadPoolFactory() {
        return new ThreadPoolFactory();
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
            taskExecutors.forEach(this::autoDecorateExecutor);
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