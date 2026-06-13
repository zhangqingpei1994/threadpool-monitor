package com.my.threadpool.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.my.threadpool.config.DiamondConfig;
import com.my.threadpool.config.ThreadPoolProperties;
import com.my.threadpool.handler.MonitorRejectedHandler;

import javax.annotation.PostConstruct;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 动态线程池
 * 支持动态修改核心线程数、最大线程数、空闲线程存活时间
 * @author zhangqingpei
 */
public class DynamicThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicThreadPoolExecutor.class);

    private final String poolName;
    private final ThreadPoolTaskExecutor executor;
    private final DiamondConfig diamondConfig;
    private final MonitorRejectedHandler rejectedHandler;

    public DynamicThreadPoolExecutor(String poolName, ThreadPoolTaskExecutor executor,
                                     DiamondConfig diamondConfig, MonitorRejectedHandler rejectedHandler) {
        this.poolName = poolName;
        this.executor = executor;
        this.diamondConfig = diamondConfig;
        this.rejectedHandler = rejectedHandler;
        this.rejectedHandler.setPoolName(poolName);
    }

    @PostConstruct
    public void init() {
        // 注册配置
        ThreadPoolProperties properties = new ThreadPoolProperties();
        DiamondConfig.DynamicThreadPoolConfig config = new DiamondConfig.DynamicThreadPoolConfig();
        config.setCorePoolSize(properties.getCorePoolSize());
        config.setMaxPoolSize(properties.getMaxPoolSize());
        config.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        config.setQueueCapacity(properties.getQueueCapacity());
        diamondConfig.registerConfig(poolName, config);

        LOGGER.info("动态线程池[{}]初始化完成", poolName);
    }

    /**
     * 动态更新线程池参数
     */
    public void updatePoolSize(int corePoolSize, int maxPoolSize, int keepAliveSeconds) {
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();
        if (threadPool == null) {
            LOGGER.warn("线程池[{}]尚未初始化，无法更新参数", poolName);
            return;
        }

        LOGGER.info("线程池[{}]参数更新: corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}",
                poolName, corePoolSize, maxPoolSize, keepAliveSeconds);

        // 更新核心线程数
        executor.setCorePoolSize(corePoolSize);

        // 更新最大线程数
        executor.setMaxPoolSize(maxPoolSize);

        // 更新空闲线程存活时间
        executor.setKeepAliveSeconds(keepAliveSeconds);

        // 同步更新diamond配置
        DiamondConfig.DynamicThreadPoolConfig config = diamondConfig.getConfig(poolName);
        if (config != null) {
            config.setCorePoolSize(corePoolSize);
            config.setMaxPoolSize(maxPoolSize);
            config.setKeepAliveSeconds(keepAliveSeconds);
        }
    }

    /**
     * 获取当前线程池执行器
     */
    public ThreadPoolTaskExecutor getExecutor() {
        return executor;
    }

    /**
     * 获取线程池名称
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * 获取被拒绝的任务数
     */
    public long getRejectedCount() {
        return rejectedHandler.getRejectedCount();
    }

    /**
     * 重置拒绝计数
     */
    public void resetRejectedCount() {
        rejectedHandler.resetCount();
    }
}