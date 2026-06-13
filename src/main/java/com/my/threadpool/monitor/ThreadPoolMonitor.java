package com.my.threadpool.monitor;

import com.my.threadpool.ThreadPoolHolder;
import com.my.threadpool.handler.MonitorRejectedHandler;
import com.my.threadpool.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
public class ThreadPoolMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolMonitor.class);
    
    private static final String LOCAL_IP = IpUtil.getIpV4Address();

    @Autowired
    private ThreadPoolMonitorProperties monitorProperties;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    @PostConstruct
    public void init() {
        // 自动注册 Spring 容器中已有的 ThreadPoolTaskExecutor
        Map<String, ThreadPoolTaskExecutor> beans = applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
        beans.forEach((name, executor) -> {
            if (ThreadPoolHolder.get(name) == null) {
                register(name, executor);
            }
        });

        // 自动注册 Spring 容器中已有的 ThreadPoolExecutor
        Map<String, ThreadPoolExecutor> threadPoolExecutorMap = applicationContext.getBeansOfType(ThreadPoolExecutor.class);
        threadPoolExecutorMap.forEach((name, executor) -> {
            if (ThreadPoolHolder.get(name) == null) {
                register(name, executor);
            }
        });


    }

    /**
     * 注册线程池到监控器（委托给 ThreadPoolHolder）
     */
    public void register(String name, ThreadPoolTaskExecutor executor) {
        ThreadPoolHolder.register(name, executor.getThreadPoolExecutor());
        LOGGER.info("[{}] 线程池[{}]已注册到监控器", LOCAL_IP, name);
    }

    /**
     * 注册线程池到监控器（委托给 ThreadPoolHolder）
     */
    public void register(String name, ThreadPoolExecutor executor) {
        ThreadPoolHolder.register(name, executor);
        LOGGER.info("[{}] 线程池[{}]已注册到监控器", LOCAL_IP, name);
    }

    /**
     * 取消注册线程池（委托给 ThreadPoolHolder）
     */
    public void unregister(String name) {
        ThreadPoolHolder.unregister(name);
        LOGGER.info("[{}] 线程池[{}]已从监控器移除", LOCAL_IP, name);
    }

    /**
     * 获取已注册的线程池（从 ThreadPoolHolder 获取）
     */
    public ThreadPoolExecutor getExecutor(String name) {
        return ThreadPoolHolder.get(name);
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
        
        LOGGER.info("========== [{}] 线程池状态监控 ==========", LOCAL_IP);
        Map<String, ThreadPoolExecutor> executors = ThreadPoolHolder.getAll();
        executors.forEach((name, pool) -> printExecutorStatus(name, pool));
        LOGGER.info("============================================");
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

        LOGGER.info("[{}] 线程池[{}] 状态: 核心线程={}, 最大线程={}, 活跃线程={}, 空闲线程={}, 队列任务数={}, 已完成任务={}, 拒绝任务数={}, allowCoreThreadTimeOut={}",
                LOCAL_IP,
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
