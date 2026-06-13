package com.my.threadpool.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zhangqingpei
 * 控制是否打开线程池监控
 */
@Data
@ConfigurationProperties(prefix = "thread.pool.monitor")
public class ThreadPoolMonitorProperties {

    private String applicationName;

    private boolean openMonitor = true;

    private boolean dynamicModifier = true;

    private int initialDelay = 30;

    private int period = 30;
}
