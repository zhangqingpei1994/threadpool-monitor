package com.my.threadpool.monitor;

import com.my.threadpool.ThreadPoolHolder;
import com.my.threadpool.autoconfig.ThreadPoolMonitorProperties;
import com.my.threadpool.handler.MonitorRejectedHandler;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 动态线程池监控器
 * 负责管理线程池状态监控、定时打印线程池状态
 * 线程池实例统一由 ThreadPoolHolder 管理
 * @author zhangqingpei
 */
@Component
public class DynamicThreadPoolMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicThreadPoolMonitor.class);


    @Autowired
    private ThreadPoolMonitorProperties monitorProperties;


    /**
     * 注册线程池到监控器（实际委托给 ThreadPoolHolder）
     */
    public void register(String name, ThreadPoolTaskExecutor executor) {
        ThreadPoolHolder.register(name, executor);
        LOGGER.info("线程池[{}]已注册到监控器", name);
    }



    /**
     * 定时打印线程池状态
     */
    @Scheduled(initialDelayString = "${thread.pool.monitor.initial-delay:60000}",
              fixedDelayString = "${thread.pool.monitor.period:60000}")
    public void printPoolStatus() {
        if (!monitorProperties.isOpenMonitor()) {
            return;
        }
        
        LOGGER.info("========== 线程池状态监控 ==========");
        Map<String, ThreadPoolExecutor> executors = ThreadPoolHolder.getAll();
        executors.forEach(this::printExecutorStatus);
        LOGGER.info("===================================");
    }

    private void printExecutorStatus(String name, ThreadPoolExecutor pool) {
        int corePoolSize = pool.getCorePoolSize();
        int maxPoolSize = pool.getMaximumPoolSize();
        int activeCount = pool.getActiveCount();
        long completedTaskCount = pool.getCompletedTaskCount();

        // 计算空闲线程数
        int idleThreads = corePoolSize - activeCount;
        if (idleThreads < 0) {
            idleThreads = 0;
        }

        // 获取队列信息
        int queueSize = 0;
        if (pool.getQueue() != null) {
            queueSize = pool.getQueue().size();
        }

        // 获取拒绝任务数
        long rejectedCount = getRejectedCount(pool);

        LOGGER.info("线程池[{}] 状态: 核心线程={}, 最大线程={}, 活跃线程={}, 空闲线程={}, 队列任务数={}, 已完成任务={}, 拒绝任务数={}, allowCoreThreadTimeOut={}",
                name,
                corePoolSize,
                maxPoolSize,
                activeCount,
                idleThreads,
                queueSize,
                completedTaskCount,
                rejectedCount,
                pool.allowsCoreThreadTimeOut());
    }

    /**
     * 获取拒绝任务数
     */
    private long getRejectedCount(ThreadPoolExecutor pool) {
        RejectedExecutionHandler handler = pool.getRejectedExecutionHandler();
        if (handler instanceof MonitorRejectedHandler) {
            return ((MonitorRejectedHandler) handler).getRejectedCount();
        }
        return 0;
    }

    /**
     * 获取线程池状态快照
     */
    public PoolStatusSnapshot getPoolStatusSnapshot(String name) {
        ThreadPoolExecutor pool = ThreadPoolHolder.get(name);
        if (pool == null) {
            return null;
        }
        PoolStatusSnapshot snapshot = new PoolStatusSnapshot();
        snapshot.setPoolName(name);
        snapshot.setCorePoolSize(pool.getCorePoolSize());
        snapshot.setMaxPoolSize(pool.getMaximumPoolSize());
        snapshot.setActiveCount(pool.getActiveCount());
        snapshot.setCompletedTaskCount(pool.getCompletedTaskCount());
        snapshot.setQueueSize(pool.getQueue() != null ? pool.getQueue().size() : 0);
        snapshot.setRejectedCount(getRejectedCount(pool));
        snapshot.setAllowCoreThreadTimeOut(pool.allowsCoreThreadTimeOut());
        return snapshot;
    }

    /**
     * 线程池状态快照
     */
    @Data
    public static class PoolStatusSnapshot {
        private String poolName;
        private int corePoolSize;
        private int maxPoolSize;
        private int activeCount;
        private long completedTaskCount;
        private int queueSize;
        private long rejectedCount;
        private boolean allowCoreThreadTimeOut;
    }
}