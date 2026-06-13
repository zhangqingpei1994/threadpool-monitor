package com.my.threadpool.decorator;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author zhangqingpei
 */
public class AlRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AlRunnable.class);

    private final Runnable runnable;

    private final Map<String, String> mdcCtx;

    private Thread currentThread;

    
    public AlRunnable(Runnable runnable) {
        this.runnable = runnable;
        this.mdcCtx = captureMdcContext();
        this.currentThread = Thread.currentThread();
    }

    @Override
    public void run() {
        // 保存执行线程的原始MDC上下文
        Map<String, String> originalMdc = captureMdcContext();
        try {
            // 将父线程上下文填充到当前线程
            if (mdcCtx != null) {
                MDC.setContextMap(mdcCtx);
            }
            // 执行实际任务
            runnable.run();
        }
        catch (Exception e) {
            logger.error("run task error=", e);
        }
        finally {
            // 线程不一致，先清理上下文
            if (currentThread != Thread.currentThread()) {
                MDC.clear();
            }
            // 恢复执行线程的原始MDC上下文
            if (originalMdc != null) {
                originalMdc.forEach(MDC::put);
            }
        }
    }

    /**
     * 捕获当前线程的MDC上下文
     */
    private Map<String, String> captureMdcContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context != null ? new java.util.HashMap<>(context) : null;
    }
}
