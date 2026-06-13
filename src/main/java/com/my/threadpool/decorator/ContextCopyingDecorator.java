package com.my.threadpool.decorator;


import org.springframework.core.task.TaskDecorator;

/**
 * 上下文传递装饰器
 * 用于在线程池执行任务时，自动传递主线程的上下文（如RequestContextHolder中的上下文）
 * @author zhangqingpei
 */
public class ContextCopyingDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return new AlRunnable(runnable);
    }
}