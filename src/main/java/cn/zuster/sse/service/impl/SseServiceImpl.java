package cn.zuster.sse.service.impl;

import cn.zuster.sse.exception.SseException;
import cn.zuster.sse.service.SseService;
import cn.zuster.sse.session.SseSession;
import cn.zuster.sse.task.AsyncDataTask;
import cn.zuster.sse.task.HeartBeatTask;
import cn.zuster.sse.util.SseEmitterUTF8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 相关业务实现
 *
 * @author zuster
 * @date 2021/1/5
 */
@Service
public class SseServiceImpl implements SseService {
    private static final Logger logger = LoggerFactory.getLogger(SseServiceImpl.class);
    
    // 存储客户端连接是否需要自动关闭的标志
    private final Map<String, Boolean> autoCloseFlags = new ConcurrentHashMap<>();
    
    // 心跳任务线程池
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    /**
     * 新建连接
     *
     * @param clientId 客户端ID
     * @param autoCloseAfterData 数据发送后是否自动关闭连接
     * @return
     */
    @Override
    public SseEmitter start(String clientId, Boolean autoCloseAfterData) {
        // 默认30秒超时,设置为0L则永不超时
        // 设置为0表示永不超时，因为我们的异步任务需要1分钟
        // 使用UTF-8编码的SseEmitter解决中文乱码问题
        SseEmitter emitter = new SseEmitterUTF8(0L);
        logger.info("MSG: SseConnect | EmitterHash: {} | ID: {} | AutoClose: {} | Date: {}", 
                emitter.hashCode(), clientId, autoCloseAfterData, new Date());
        
        // 保存自动关闭标志
        autoCloseFlags.put(clientId, autoCloseAfterData);
        
        // 添加到会话管理
        SseSession.add(clientId, emitter);
        
        try {
            // 发送连接成功消息
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("连接已建立")
                    .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            logger.error("MSG: Error sending initial message | ID: {} | Error: {}", clientId, e.getMessage());
        }
        
        // 检查是否有缓存的任务结果，有则立即发送
        boolean hasCachedResult = AsyncDataTask.hasTaskResult(clientId);
        if (hasCachedResult) {
            logger.info("MSG: Found cached result on reconnect | ID: {} | Date: {}", clientId, new Date());
            AsyncDataTask.sendCachedResultIfExists(clientId);
            
            // 如果设置了自动关闭，发送完缓存数据后关闭连接
            if (autoCloseAfterData) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("end")
                            .id(String.valueOf(System.currentTimeMillis())));
                    close(clientId);
                } catch (IOException e) {
                    logger.error("MSG: Error sending end message | ID: {} | Error: {}", clientId, e.getMessage());
                }
            } else {
                // 启动心跳任务
                startHeartbeat(clientId);
            }
        }
        
        // 检查是否已有任务在运行，没有则启动新任务
        boolean taskStarted = AsyncDataTask.hasRunningTask(clientId);
        if (taskStarted) {
            logger.info("MSG: AsyncTask already running for client | ID: {} | Date: {}", clientId, new Date());
        } else if (!hasCachedResult) { // 只有在没有缓存结果且没有运行任务的情况下才启动新任务
            // 启动异步任务
            AsyncDataTask.startAsyncTask(clientId);
            
            // 如果不需要自动关闭，启动心跳任务
            if (!autoCloseAfterData) {
                startHeartbeat(clientId);
            }
        }
        
        emitter.onCompletion(() -> {
            logger.info("MSG: SseConnectCompletion | EmitterHash: {} | ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onCompletion(clientId, null);
            autoCloseFlags.remove(clientId);
        });
        emitter.onTimeout(() -> {
            logger.error("MSG: SseConnectTimeout | EmitterHash: {} | ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onError(clientId, new SseException("TimeOut(clientId: " + clientId + ")"));
            autoCloseFlags.remove(clientId);
        });
        emitter.onError(t -> {
            logger.error("MSG: SseConnectError | EmitterHash: {} | ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onError(clientId, new SseException("Error(clientId: " + clientId + ")"));
            autoCloseFlags.remove(clientId);
        });
        return emitter;
    }

    /**
     * 启动心跳任务，每5秒发送一次心跳
     * 
     * @param clientId 客户端ID
     */
    private void startHeartbeat(String clientId) {
        heartbeatExecutor.scheduleAtFixedRate(
            new HeartBeatTask(clientId), 
            5, 
            5, 
            TimeUnit.SECONDS
        );
    }

    /**
     * 发送数据
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public String send(String clientId) {
        Boolean autoClose = autoCloseFlags.getOrDefault(clientId, false);
        
        if (SseSession.send(clientId, System.currentTimeMillis())) {
            // 如果设置了自动关闭，发送完数据后发送结束标记并关闭连接
            if (autoClose) {
                try {
                    SseSession.send(clientId, "end");
                    close(clientId);
                } catch (Exception e) {
                    logger.error("MSG: Error sending end message | ID: {} | Error: {}", clientId, e.getMessage());
                }
            }
            return "Succeed!";
        }
        return "error";
    }

    /**
     * 关闭连接
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public String close(String clientId) {
        logger.info("MSG: SseConnectClose | ID: {} | Date: {}", clientId, new Date());
        autoCloseFlags.remove(clientId);
        if (SseSession.del(clientId)) return "Succeed!";
        return "Error!";
    }
}
