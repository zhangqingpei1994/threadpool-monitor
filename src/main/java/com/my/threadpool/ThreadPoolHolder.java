package com.my.threadpool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池持有器
 * 用于全局持有线程池实例，支持按名称获取
 * @author zhangqingpei
 */
public class ThreadPoolHolder {

    private static volatile Map<String, ThreadPoolExecutor> threadPools = new ConcurrentHashMap<>();

    /**
     * 注册线程池
     */
    public static void register(String name, ThreadPoolExecutor executor) {
        threadPools.put(name, executor);
    }

    /**
     * 注销线程池
     */
    public static void unregister(String name) {
        threadPools.remove(name);
    }

    /**
     * 获取线程池
     */
    public static ThreadPoolExecutor get(String name) {
        return threadPools.get(name);
    }

    /**
     * 获取所有线程池
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
