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
     */
    @Override
    public SseEmitter start(String clientId, Boolean autoCloseAfterData) {
        // 使用UTF-8编码的SseEmitter解决中文乱码问题
        SseEmitter emitter = new SseEmitterUTF8(0L);
        
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
        if (!taskStarted && !hasCachedResult) {
            // 启动异步任务
            AsyncDataTask.startAsyncTask(clientId);
            
            // 如果不需要自动关闭，启动心跳任务
            if (!autoCloseAfterData) {
                startHeartbeat(clientId);
            }
        }
        
        // 注册回调
        emitter.onCompletion(() -> {
            SseSession.onCompletion(clientId, null);
            autoCloseFlags.remove(clientId);
        });
        emitter.onTimeout(() -> {
            SseSession.onError(clientId, new SseException("TimeOut(clientId: " + clientId + ")"));
            autoCloseFlags.remove(clientId);
        });
        emitter.onError(t -> {
            SseSession.onError(clientId, new SseException("Error(clientId: " + clientId + ")"));
            autoCloseFlags.remove(clientId);
        });
        
        return emitter;
    }

    /**
     * 启动心跳任务，每5秒发送一次心跳
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
     */
    @Override
    public String close(String clientId) {
        autoCloseFlags.remove(clientId);
        if (SseSession.del(clientId)) return "Succeed!";
        return "Error!";
    }
}