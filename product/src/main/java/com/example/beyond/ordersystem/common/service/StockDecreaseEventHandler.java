//package com.example.beyond.ordersystem.common.service;
//
//import com.example.beyond.ordersystem.common.config.RabbitmqConfigs;
//import com.example.beyond.ordersystem.product.repository.ProductRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.amqp.core.Message;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.persistence.EntityNotFoundException;
//import java.io.IOException;
//
//@Component
//public class StockDecreaseEventHandler {
//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    public void publish(StockDecreaseEvent event){
//        rabbitTemplate.convertAndSend(RabbitmqConfigs.STOCK_DECREASE_QUE, event);
//    }
//    // transaction이 완료된 이후에 그다음 메시지 수신하므로, 동시성 이슈 발생x
//    @Transactional
//    @RabbitListener(queues = RabbitmqConfigs.STOCK_DECREASE_QUE)
//    public void listen(Message message){
////        String messageBody = new String(message.getBody());
////        System.out.println(messageBody);
////        StockDecreaseEvent event = null;
////        // 	json 메시지를 parsing -> ObjectMapper/ StockDecreaseEvent
////        ObjectMapper objectMapper = new ObjectMapper();
////        try {
////            event = objectMapper.readValue(messageBody, StockDecreaseEvent.class);
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
////
////        // 	재고 업데이트
////        productRepository.findById(event.getProductId())
////                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 물품입니다."))
////                .UpdatStockQuantity(event.getProductCount());
//    }
//
//}
