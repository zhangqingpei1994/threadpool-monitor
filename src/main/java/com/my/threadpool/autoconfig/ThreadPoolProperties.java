package com.my.threadpool.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池默认配置属性
 * @author zhangqingpei
 */
@ConfigurationProperties(prefix = "thread.pool")
@Data
public class ThreadPoolProperties {

    /**
     * 核心线程数
     */
    private int corePoolSize = 10;

    /**
     * 最大线程数
     */
    private int maxPoolSize = 20;

    /**
     * 空闲线程存活时间（秒）
     */
    private int keepAliveSeconds = 60;

    /**
     * 队列容量
     */
    private int queueCapacity = 1000;

    /**
     * 线程名前缀
     */
    private String threadNamePrefix = "dynamic-pool-";

    /**
     * 是否允许核心线程超时
     */
    private boolean allowCoreThreadTimeOut = false;
}