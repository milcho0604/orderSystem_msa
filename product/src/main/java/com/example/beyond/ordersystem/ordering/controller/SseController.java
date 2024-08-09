package com.example.beyond.ordersystem.ordering.controller;

import com.example.beyond.ordersystem.ordering.dto.OrderListResDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SseController implements MessageListener {
    // SseEmitter : 연결된 사용자 정보
    // ConcurrentHashMap : Thread-safe map(동시성 이슈가 발생하지 않는 map)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // 여러번 구독을 벙지하기 위한  ConcurrentHashSet 변수 생성
    private Set<String> subscribeList = ConcurrentHashMap.newKeySet();

    @Qualifier("4")
    private final RedisTemplate<String, Object> sseRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;


    public SseController(@Qualifier("4") RedisTemplate<String, Object> sseRedisTemplate, RedisMessageListenerContainer redisMessageListenerContainer) {
        this.sseRedisTemplate = sseRedisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    // email 에 해당되는 메시지를 listen 하는 listener를 츄가한 것.
    public void subscribeChannel(String email) {
        // 이미 구독한 email일 경우에 더이상 구독하지 않는 분기 처리
        if (!subscribeList.contains(email)) {
            MessageListenerAdapter listenerAdapter = creatListenerAdapter(this);
            redisMessageListenerContainer.addMessageListener(listenerAdapter, new PatternTopic(email));
            subscribeList.add(email);
        }
    }

    private MessageListenerAdapter creatListenerAdapter(SseController sseController) {
        return new MessageListenerAdapter(sseController, "onMessage");
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(14400 * 60 * 1000L); // 60분 정도로 emitter 접근 시간 설정
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        emitters.put(email, emitter);
        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        try {
            emitter.send(SseEmitter.event().name("connect").data("!!!!connect!!!!"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        subscribeChannel(email);
        return emitter;
    }

    public void publishMessage(OrderListResDto dto, String email) {
//        SseEmitter emitter = emitters.get(email);
//        if (emitter != null) {
//            try {
//                emitter.send(SseEmitter.event().name("ordered").data(dto));
//            }catch (IOException e){
//                throw new RuntimeException(e);
//            }
//        }else {
        sseRedisTemplate.convertAndSend(email, dto);
//        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // message 내용 parsing
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            OrderListResDto dto = objectMapper.readValue(message.getBody(), OrderListResDto.class);
            String email = new String(pattern, StandardCharsets.UTF_8);
            SseEmitter emitter = emitters.get(email);
            if (emitter != null){
                emitter.send(SseEmitter.event().name("ordered").data(dto));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
