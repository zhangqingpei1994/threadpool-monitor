package com.my.threadpool.monitor;

import com.my.threadpool.ThreadPoolHolder;
import com.my.threadpool.handler.MonitorRejectedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
public class DynamicThreadPoolMonitor extends ThreadPoolHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicThreadPoolMonitor.class);

    @Autowired
    private ThreadPoolMonitorProperties monitorProperties;


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
}