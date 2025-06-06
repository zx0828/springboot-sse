package cn.zuster.sse.task;

import cn.zuster.sse.session.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 异步数据处理任务
 * 模拟持续1分钟的耗时任务，任务完成后推送数据给前端
 */
public class AsyncDataTask {
    private static final Logger logger = LoggerFactory.getLogger(AsyncDataTask.class);
    
    /**
     * 任务状态跟踪Map：clientId -> 任务状态(true表示正在运行)
     */
    private static final Map<String, Boolean> RUNNING_TASKS = new ConcurrentHashMap<>();
    
    /**
     * 任务结果缓存：clientId -> 结果数据
     * 用于在客户端断开重连后仍能获取到结果
     */
    private static final Map<String, Object> TASK_RESULTS = new ConcurrentHashMap<>();
    
    /**
     * 最大数据保留时间（毫秒），默认5分钟
     */
    private static final long MAX_RESULT_RETENTION_MS = 5 * 60 * 1000;
    
    /**
     * 发送重试最大次数
     */
    private static final int MAX_SEND_RETRIES = 3;
    
    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL_MS = 1000;

    /**
     * 检查客户端是否有正在运行的任务
     * @param clientId 客户端ID
     * @return 是否有任务正在运行
     */
    public static boolean hasRunningTask(String clientId) {
        return RUNNING_TASKS.containsKey(clientId) && RUNNING_TASKS.get(clientId);
    }
    
    /**
     * 检查任务是否有缓存的结果数据
     * @param clientId 客户端ID
     * @return 是否有缓存的结果
     */
    public static boolean hasTaskResult(String clientId) {
        return TASK_RESULTS.containsKey(clientId);
    }
    
    /**
     * 尝试向客户端发送缓存的结果数据
     * @param clientId 客户端ID
     * @return 是否成功发送
     */
    public static boolean sendCachedResultIfExists(String clientId) {
        if (!hasTaskResult(clientId)) {
            return false;
        }
        
        Object result = TASK_RESULTS.get(clientId);
        boolean sent = SseSession.send(clientId, result);
        
        if (sent) {
            logger.info("MSG: Cached result sent to reconnected client | ID: {} | Date: {}", clientId, new Date());
            // 发送成功后移除缓存
            TASK_RESULTS.remove(clientId);
        }
        
        return sent;
    }

    /**
     * 启动异步任务处理
     * @param clientId 客户端ID
     * @return 是否成功启动任务
     */
    public static boolean startAsyncTask(String clientId) {
        // 如果已经有任务在运行，则不启动新任务
        if (hasRunningTask(clientId)) {
            logger.info("MSG: AsyncDataTask already running for client | ID: {} | Date: {}", clientId, new Date());
            return false;
        }
        
        // 检查是否有缓存的结果，有则直接发送
        if (sendCachedResultIfExists(clientId)) {
            logger.info("MSG: Found cached result for client, sent immediately | ID: {} | Date: {}", clientId, new Date());
            return true;
        }
        
        // 标记该clientId有任务正在运行
        RUNNING_TASKS.put(clientId, true);
        logger.info("MSG: AsyncDataTask started | ID: {} | Date: {}", clientId, new Date());
        
        CompletableFuture.runAsync(() -> {
            try {
                // 模拟耗时任务，持续15秒（为了测试方便，缩短了时间）
                Thread.sleep(TimeUnit.SECONDS.toMillis(15));
                
                // 任务完成后，构造数据并发送
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("status", "completed");
                resultData.put("message", "异步任务处理完成");
                resultData.put("timestamp", System.currentTimeMillis());
                resultData.put("data", "这是异步处理的结果数据");
                
                // 存储结果以备重连使用
                TASK_RESULTS.put(clientId, resultData);
                
                // 创建一个单独的线程来发送数据，带有重试机制
                trySendResultWithRetry(clientId, resultData);
                
                // 启动清理任务，在指定时间后清除结果缓存
                scheduleResultCleanup(clientId);
                
            } catch (InterruptedException e) {
                logger.error("MSG: AsyncDataTask interrupted | ID: {} | Date: {}", clientId, new Date(), e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("MSG: AsyncDataTask error | ID: {} | Date: {}", clientId, new Date(), e);
            } finally {
                // 任务完成后，移除任务标记
                RUNNING_TASKS.remove(clientId);
                logger.info("MSG: AsyncDataTask cleaned up | ID: {} | Date: {}", clientId, new Date());
            }
        });
        
        return true;
    }
    
    /**
     * 尝试发送结果数据给客户端，带有重试机制
     * @param clientId 客户端ID
     * @param resultData 结果数据
     */
    private static void trySendResultWithRetry(String clientId, Object resultData) {
        CompletableFuture.runAsync(() -> {
            boolean sent = false;
            int retries = 0;
            
            // 先检查会话是否存在
            if (!SseSession.exist(clientId)) {
                logger.info("MSG: Client session not active, will keep result cached | ID: {} | Date: {}", clientId, new Date());
                return;
            }
            
            // 尝试发送，最多重试MAX_SEND_RETRIES次
            while (!sent && retries < MAX_SEND_RETRIES) {
                try {
                    sent = SseSession.send(clientId, resultData);
                    
                    if (sent) {
                        logger.info("MSG: AsyncDataTask completed | Data sent | ID: {} | Retry: {} | Date: {}", 
                                clientId, retries, new Date());
                    } else {
                        retries++;
                        if (retries < MAX_SEND_RETRIES) {
                            logger.warn("MSG: Failed to send data, will retry | ID: {} | Retry: {} | Date: {}", 
                                    clientId, retries, new Date());
                            Thread.sleep(RETRY_INTERVAL_MS);
                        } else {
                            logger.error("MSG: AsyncDataTask completed | Failed to send data after retries | ID: {} | Date: {}", 
                                    clientId, new Date());
                        }
                    }
                } catch (Exception e) {
                    retries++;
                    logger.error("MSG: Error sending data | ID: {} | Retry: {} | Error: {}", 
                            clientId, retries, e.getMessage());
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * 安排结果缓存清理
     * @param clientId 客户端ID
     */
    private static void scheduleResultCleanup(String clientId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(MAX_RESULT_RETENTION_MS);
                if (TASK_RESULTS.containsKey(clientId)) {
                    TASK_RESULTS.remove(clientId);
                    logger.info("MSG: Cleaned up cached result | ID: {} | Date: {}", clientId, new Date());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
} 