package com.example.beyond.ordersystem.ordering.service;

import com.example.beyond.ordersystem.common.service.StockDecreaseEventHandler;
import com.example.beyond.ordersystem.common.service.StockInventoryService;
import com.example.beyond.ordersystem.ordering.controller.SseController;
import com.example.beyond.ordersystem.ordering.domain.OrderStatus;
import com.example.beyond.ordersystem.ordering.domain.Ordering;
import com.example.beyond.ordersystem.ordering.dto.OrderListResDto;
import com.example.beyond.ordersystem.ordering.dto.OrderSaveReqDto;
import com.example.beyond.ordersystem.ordering.repository.OrderDetailRepository;
import com.example.beyond.ordersystem.ordering.repository.OrderingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;


@Service
@Transactional
public class OrderingService {

    private final OrderingRepository orderingRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final StockInventoryService stockInventoryService;
    private final SseController sseController;
    private final StockDecreaseEventHandler stockDecreaseEventHandler;

    @Autowired
    public OrderingService(OrderingRepository orderingRepository, OrderDetailRepository orderDetailRepository, StockInventoryService stockInventoryService, SseController sseController, StockDecreaseEventHandler stockDecreaseEventHandler) {
        this.orderingRepository = orderingRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.stockInventoryService = stockInventoryService;
        this.sseController = sseController;
        this.stockDecreaseEventHandler = stockDecreaseEventHandler;
    }

    // Synchronized : 설정한다고 하더라도, 재고 감소가 DB에 반영되는 시점은 트랜잭션이 커밋되고 종료되는 시점이다
    @Transactional
    public Ordering orderCreate(@ModelAttribute List<OrderSaveReqDto> dtos) {

        // 방법3 : 스프링 시큐리티를 통한 주문 생성(토큰을 통한 사용자 인증), (getName = email)
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName(); // 중요 !!
        Ordering ordering = Ordering.builder()
                .memberEmail(memberEmail)
                .build();
        // OrderDetail생성 : order_id, product_id, quantity
//        for (OrderSaveReqDto dto : dtos) {
//            int quantity = dto.getProductCount();
//            // Product API에 요청을 통해 Product 객체를 조히해야한다
//            if (product.getName().contains("sale")) {
//                // redis를 통한 재고관리 및 재고잔량 확인
//                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
//                if(newQuantity<0){
//                    throw new IllegalArgumentException("(redis) 재고가 부족합니다.");
//                }
//                // RDB 재고를 업데이트 : rabbitmq 통해 비동기적으로 이벤트 처리
//                stockDecreaseEventHandler.publish(new StockDecreaseEvent(product.getId(), dto.getProductCount()));
//
//            } else {
//                if (quantity > product.getStock_quantity()) {
//                    throw new IllegalArgumentException("재고가 부족합니다");
//                } else {
//                    // 변경감지로 인해 별도의 save 불필요
//                    product.UpdatStockQuantity(quantity);
//                }
//            }
//            OrderDetail orderDetail = OrderDetail.builder()
//                    .product(product)
//                    .quantity(quantity)
//                    .ordering(ordering)
//                    .build();
//                    // orderingRepository.save(ordering);을 하지 않아,
//                    // ordering_id 는 아직 생성되지 않았지만, JPA가 자동으로 순서를 정렬하여 ordering_id 를 삽입한다.build();
//            ordering.getOrderDetails().add(orderDetail);
//        }
        Ordering savedOreder = orderingRepository.save(ordering);

        // 이메일을 유저이메일
        sseController.publishMessage(savedOreder.fromEntity(), "admin@test.com");
        return savedOreder;
    }

    @Transactional
    public List<OrderListResDto> orderList() {
        List<Ordering> orderings = orderingRepository.findAll();
        List<OrderListResDto> orderListResDtos = new ArrayList<>();
        for (Ordering ordering : orderings) {
            orderListResDtos.add(ordering.fromEntity());
        }
        return orderListResDtos;
    }

    @Transactional
    public List<OrderListResDto> myOrders() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Ordering> orderings = orderingRepository.findByMemberEmail(email);
        List<OrderListResDto> orderListResDtos = new ArrayList<>();
        for (Ordering ordering : orderings) {
            orderListResDtos.add(ordering.fromEntity());
        }
        return orderListResDtos;
    }

    @Transactional
    public Ordering orderCancel(Long id) {
        Ordering ordering = orderingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 주문입니다."));
        ordering.updateStatus(OrderStatus.CANCELED);
        return ordering;
    }


    //        //        방법1.쉬운방식
////        Ordering생성 : member_id, status
//        Member member = memberRepository.findById(dto.getMember_id()).orElseThrow(() -> new EntityNotFoundException("없음"));
//        Ordering ordering = orderingRepository.save(dto.toEntity(member));
//
////        OrderDetail생성 : order_id, product_id, quantity
//        for (OrderSaveReqDto.OrderDetailDto orderDto : dto.getOrderDetailDtoList()) {
//            Product product = productRepository.findById(orderDto.getProductId()).orElse(null);
//            int quantity = orderDto.getProductCount();
//            OrderDetail orderDetail = OrderDetail.builder()
//                    .product(product)
//                    .quantity(quantity)
//                    .ordering(ordering)
//                    .build();
//            orderDetailRepository.save(orderDetail);
//        }
//        return ordering;
//    }

    // 방법2 : JPA 최적화된 방식
    // Ordering 생성: member_id, status
//        Member member = memberRepository.findById(dto.getMember_id())
//                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));
//
//        Ordering ordering = Ordering.builder()
//                .member(member)
//                .build();
//        // OrderDetail생성 : order_id, product_id, quantity
//        for (OrderSaveReqDto.OrderDetailDto orderDto : dto.getOrderDetailDtoList()) {
//            Product product = productRepository.findById(orderDto.getProductId())
//                    .orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다."));
//            int quantity = orderDto.getProductCount();
//            if(quantity > product.getStock_quantity()){
//                throw new IllegalArgumentException("재고가 부족합니다");
//            }else {
//                // 변경감지로 인해 별도의 save 불필요
//                product.UpdatStockQuantity(quantity);
//            }
//            OrderDetail orderDetail = OrderDetail.builder()
//                    .product(product)
//                    .quantity(quantity)
//                    .ordering(ordering)
//                    // orderingRepository.save(ordering);을 하지 않아,
//                    // ordering_id 는 아직 생성되지 않았지만, JPA가 자동으로 순서를 정렬하여 ordering_id 를 삽입한다.
//                    .build();
//            ordering.getOrderDetails().add(orderDetail);
//        }
//        Ordering savedOreder = orderingRepository.save(ordering);
//        return savedOreder;
//    }

}
