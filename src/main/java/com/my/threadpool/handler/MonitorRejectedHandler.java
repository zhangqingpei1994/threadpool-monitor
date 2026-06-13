package com.my.threadpool.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可监控的拒绝策略
 * 包装原有的拒绝策略，同时统计被拒绝的任务数量
 * @author zhangqingpei
 */
public class MonitorRejectedHandler implements RejectedExecutionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorRejectedHandler.class);

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private volatile String poolName = "unknown";

    public MonitorRejectedHandler(RejectedExecutionHandler delegate) {
        this.delegate = delegate;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        long count = rejectedCount.incrementAndGet();
        LOGGER.warn("线程池[{}]执行了拒绝策略，当前累计拒绝任务数: {}", poolName, count);

        if (delegate != null) {
            delegate.rejectedExecution(r, executor);
        }
    }

    /**
     * 获取被拒绝的任务总数
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * 重置计数器
     */
    public void resetCount() {
        rejectedCount.set(0);
    }
}