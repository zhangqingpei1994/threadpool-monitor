package com.my.threadpool.monitor;

import com.my.threadpool.autoconfig.ThreadPoolMonitorProperties;
import com.my.threadpool.handler.MonitorRejectedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 动态线程池监控器
 * 负责管理线程池注册、状态监控、定时打印线程池状态
 * @author zhangqingpei
 */
@Component
public class DynamicThreadPoolMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicThreadPoolMonitor.class);

    /**
     * 核心线程数
     */
    private volatile int corePoolSize;
    /**
     * 最大线程数
     */
    private volatile int maxPoolSize;
    /**
     * 当前活跃线程数
     */
    private volatile int activeCount;
    /**
     * 当前排队任务数
     */
    private volatile long completedTaskCount;

    private final Map<String, ThreadPoolTaskExecutor> executors = new ConcurrentHashMap<>();

    @Autowired
    private ThreadPoolMonitorProperties monitorProperties;

    @PostConstruct
    public void init() {
        LOGGER.info("DynamicThreadPoolMonitor 初始化完成, 监控开关: {}, 动态修改: {}", 
                monitorProperties.isOpenMonitor(), monitorProperties.isDynamicModifier());
    }

    /**
     * 注册线程池到监控器
     */
    public void register(String name, ThreadPoolTaskExecutor executor) {
        executors.put(name, executor);
        LOGGER.info("线程池[{}]已注册到监控器", name);
    }

    /**
     * 取消注册线程池
     */
    public void unregister(String name) {
        executors.remove(name);
        LOGGER.info("线程池[{}]已从监控器移除", name);
    }

    /**
     * 获取已注册的线程池
     */
    public ThreadPoolTaskExecutor getExecutor(String name) {
        return executors.get(name);
    }

    /**
     * 定时打印线程池状态
     */
    @Scheduled(initialDelayString = "${threadpool.monitor.initial-delay:60000}", 
              fixedDelayString = "${threadpool.monitor.period:60000}")
    public void printPoolStatus() {
        if (!monitorProperties.isOpenMonitor()) {
            return;
        }
        
        LOGGER.info("========== 线程池状态监控 ==========");
        executors.forEach((name, executor) -> {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            if (pool != null) {
                printExecutorStatus(name, pool);
            }
        });
        LOGGER.info("===================================");
    }

    private void printExecutorStatus(String name, ThreadPoolExecutor pool) {
        corePoolSize = pool.getCorePoolSize();
        maxPoolSize = pool.getMaximumPoolSize();
        activeCount = pool.getActiveCount();
        completedTaskCount = pool.getCompletedTaskCount();

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
        ThreadPoolTaskExecutor executor = executors.get(name);
        if (executor == null) {
            return null;
        }

        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
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
    public static class PoolStatusSnapshot {
        private String poolName;
        private int corePoolSize;
        private int maxPoolSize;
        private int activeCount;
        private long completedTaskCount;
        private int queueSize;
        private long rejectedCount;
        private boolean allowCoreThreadTimeOut;

        // getters and setters
        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public void setActiveCount(int activeCount) {
            this.activeCount = activeCount;
        }

        public long getCompletedTaskCount() {
            return completedTaskCount;
        }

        public void setCompletedTaskCount(long completedTaskCount) {
            this.completedTaskCount = completedTaskCount;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public long getRejectedCount() {
            return rejectedCount;
        }

        public void setRejectedCount(long rejectedCount) {
            this.rejectedCount = rejectedCount;
        }

        public boolean isAllowCoreThreadTimeOut() {
            return allowCoreThreadTimeOut;
        }

        public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
            this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
        }
    }
}