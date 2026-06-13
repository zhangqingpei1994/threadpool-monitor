package com.my.threadpool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池持有器
 * 用于全局持有线程池实例，支持按名称获取
 * @author zhangqingpei
 */
public class ThreadPoolHolder {

    private static volatile Map<String, ThreadPoolExecutor> threadPools = new ConcurrentHashMap<>();

    /**
     * 注册线程池（ThreadPoolExecutor）
     */
    public static void register(String name, ThreadPoolExecutor executor) {
        threadPools.put(name, executor);
    }

    /**
     * 注册线程池（ThreadPoolTaskExecutor）
     */
    public static void register(String name, ThreadPoolTaskExecutor executor) {
        threadPools.put(name, executor.getThreadPoolExecutor());
    }

    /**
     * 注册线程池（ThreadPoolTaskExecutor，使用线程名前缀作为key）
     */
    public static void register(ThreadPoolTaskExecutor executor) {
        String name = executor.getThreadNamePrefix();
        threadPools.put(name, executor.getThreadPoolExecutor());
    }

    /**
     * 注销线程池
     */
    public static void unregister(String name) {
        threadPools.remove(name);
    }

    /**
     * 获取线程池（ThreadPoolExecutor）
     */
    public static ThreadPoolExecutor get(String name) {
        return threadPools.get(name);
    }


    /**
     * 获取所有线程池（ThreadPoolExecutor）
     */
    public static Map<String, ThreadPoolExecutor> getAll() {
        return threadPools;
    }


    /**
     * 清除所有线程池
     */
    public static void clear() {
        threadPools.clear();
    }
}