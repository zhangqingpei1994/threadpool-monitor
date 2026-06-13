package com.my.threadpool;

import com.my.threadpool.autoconfig.ThreadPoolProperties;
import com.my.threadpool.decorator.ContextCopyingDecorator;
import com.my.threadpool.handler.MonitorRejectedHandler;
import com.my.threadpool.monitor.DynamicThreadPoolMonitor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池工厂类
 * 负责创建和管理多个线程池实例
 * @author zhangqingpei
 */
@Slf4j
@Component
public class ThreadPoolFactory {
    @Autowired
    private ThreadPoolProperties threadPoolProperties;

    @Autowired
    private DynamicThreadPoolMonitor dynamicThreadPoolMonitor;

    /**
     * 使用默认配置创建线程池
     * @param poolName 线程池名称
     * @return ThreadPoolTaskExecutor
     */
    public ThreadPoolTaskExecutor createThreadPool(String poolName) {
        return createThreadPool(poolName, 
                threadPoolProperties.getCorePoolSize(),
                threadPoolProperties.getMaxPoolSize(),
                threadPoolProperties.getKeepAliveSeconds(),
                threadPoolProperties.getQueueCapacity(),
                threadPoolProperties.isAllowCoreThreadTimeOut());
    }

    /**
     * 创建线程池（完整参数）
     * @param poolName 线程池名称
     * @param corePoolSize 核心线程数
     * @param maxPoolSize 最大线程数
     * @param keepAliveSeconds 空闲线程存活时间
     * @param queueCapacity 队列容量
     * @param allowCoreThreadTimeOut 是否允许核心线程超时
     * @return ThreadPoolTaskExecutor
     */
    public ThreadPoolTaskExecutor createThreadPool(String poolName, 
                                                   int corePoolSize, 
                                                   int maxPoolSize,
                                                   int keepAliveSeconds,
                                                   int queueCapacity,
                                                   boolean allowCoreThreadTimeOut) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 设置基础参数
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(poolName + "-");
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);

        // 设置上下文传递装饰器
        executor.setTaskDecorator(new ContextCopyingDecorator());

        // 设置拒绝策略
        MonitorRejectedHandler rejectedHandler = new MonitorRejectedHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        rejectedHandler.setPoolName(poolName);
        executor.setRejectedExecutionHandler(rejectedHandler);

        // 初始化线程池
        executor.initialize();

        // 线程预热：提前启动所有核心线程
        executor.getThreadPoolExecutor().prestartAllCoreThreads();

        // 注册到监控器
        dynamicThreadPoolMonitor.register(poolName, executor);

        log.info("线程池[{}]创建成功, 核心线程={}, 最大线程={}, 队列容量={}", 
                poolName, corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }


    /**
     * 销毁线程池
     * @param poolName 线程池名称
     */
    public void destroyThreadPool(String poolName) {
        ThreadPoolTaskExecutor executor = ThreadPoolHolder.getTaskExecutor(poolName);
        if (executor != null) {
            executor.shutdown();
            ThreadPoolHolder.unregister(poolName);
            log.info("线程池[{}]已销毁", poolName);
        }
    }

    /**
     * 获取已创建的线程池
     * @param poolName 线程池名称
     * @return ThreadPoolTaskExecutor
     */
    public ThreadPoolTaskExecutor getThreadPool(String poolName) {
        return ThreadPoolHolder.getTaskExecutor(poolName);
    }

}
