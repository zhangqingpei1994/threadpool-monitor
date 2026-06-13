package com.my.threadpool.autoconfig;

import com.my.threadpool.ThreadPoolHolder;
import com.my.threadpool.ThreadPoolFactory;
import com.my.threadpool.monitor.DynamicThreadPoolMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    private DiamondConfig diamondConfig;

    @Autowired
    private DynamicThreadPoolMonitor dynamicThreadPoolMonitor;

    /**
     * 创建线程池工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolFactory threadPoolFactory() {
        return new ThreadPoolFactory();
    }

    /**
     * 设置 Diamond 配置变更监听器
     */
    @PostConstruct
    public void setupConfigChangeListener() {
        // 注册默认配置
        DiamondConfig.DynamicThreadPoolConfig defaultConfig = new DiamondConfig.DynamicThreadPoolConfig();
        defaultConfig.setCorePoolSize(threadPoolProperties.getCorePoolSize());
        defaultConfig.setMaxPoolSize(threadPoolProperties.getMaxPoolSize());
        defaultConfig.setKeepAliveSeconds(threadPoolProperties.getKeepAliveSeconds());
        defaultConfig.setQueueCapacity(threadPoolProperties.getQueueCapacity());
        
        diamondConfig.registerConfig("default", defaultConfig);
        
        // 设置配置变更回调
        diamondConfig.setConfigChangeListener((poolName, config) -> {
            ThreadPoolExecutor executor = ThreadPoolHolder.get(poolName);
            if (executor != null) {
                LOGGER.info("线程池[{}]动态配置变更, 正在更新参数: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                        poolName, config.getCorePoolSize(), config.getMaxPoolSize(), config.getKeepAliveSeconds());
                
                executor.setCorePoolSize(config.getCorePoolSize());
                executor.setMaximumPoolSize(config.getMaxPoolSize());
                executor.setKeepAliveTime(config.getKeepAliveSeconds(), TimeUnit.SECONDS);
            }
        });
        
        // 自动注册 Spring 容器中已有的线程池配置
        autoRegisterExistingExecutors();
        
        // 订阅 ACM 配置变更
        diamondConfig.subscribeConfig();
    }

    /**
     * 自动注册 Spring 容器中已有的线程池到 Diamond 配置
     */
    private void autoRegisterExistingExecutors() {
        try {
            Map<String, ThreadPoolTaskExecutor> executors = 
                dynamicThreadPoolMonitor.getApplicationContext().getBeansOfType(ThreadPoolTaskExecutor.class);
            
            executors.forEach((name, executor) -> {
                ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
                if (pool != null) {
                    DiamondConfig.DynamicThreadPoolConfig config = new DiamondConfig.DynamicThreadPoolConfig();
                    config.setCorePoolSize(pool.getCorePoolSize());
                    config.setMaxPoolSize(pool.getMaximumPoolSize());
                    config.setKeepAliveSeconds((int) pool.getKeepAliveTime(TimeUnit.SECONDS));
                    diamondConfig.registerConfig(name, config);
                }
            });
        } catch (Exception e) {
            LOGGER.warn("自动注册线程池配置失败: {}", e.getMessage());
        }
    }
}
