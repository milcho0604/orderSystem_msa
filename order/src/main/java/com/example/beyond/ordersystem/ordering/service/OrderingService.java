package com.example.beyond.ordersystem.ordering.service;

import com.example.beyond.ordersystem.common.dto.CommonResDto;
import com.example.beyond.ordersystem.common.service.StockDecreaseEventHandler;
import com.example.beyond.ordersystem.common.service.StockInventoryService;
import com.example.beyond.ordersystem.ordering.controller.SseController;
import com.example.beyond.ordersystem.ordering.domain.OrderDetail;
import com.example.beyond.ordersystem.ordering.domain.OrderStatus;
import com.example.beyond.ordersystem.ordering.domain.Ordering;
import com.example.beyond.ordersystem.ordering.dto.OrderListResDto;
import com.example.beyond.ordersystem.ordering.dto.OrderSaveReqDto;
import com.example.beyond.ordersystem.ordering.dto.ProductDto;
import com.example.beyond.ordersystem.ordering.dto.ProductUpdateStockDto;
import com.example.beyond.ordersystem.ordering.event.StockDecreaseEvent;
import com.example.beyond.ordersystem.ordering.repository.OrderDetailRepository;
import com.example.beyond.ordersystem.ordering.repository.OrderingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;
    private final ProductFeign productFeign;

    @Autowired
    public OrderingService(OrderingRepository orderingRepository, OrderDetailRepository orderDetailRepository, StockInventoryService stockInventoryService, SseController sseController, StockDecreaseEventHandler stockDecreaseEventHandler, RestTemplate restTemplate, ProductFeign productFeign) {
        this.orderingRepository = orderingRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.stockInventoryService = stockInventoryService;
        this.sseController = sseController;
        this.stockDecreaseEventHandler = stockDecreaseEventHandler;
        this.restTemplate = restTemplate;
        this.productFeign = productFeign;
    }

    // Synchronized : 설정한다고 하더라도, 재고 감소가 DB에 반영되는 시점은 트랜잭션이 커밋되고 종료되는 시점이다
    @Transactional
    public Ordering orderRestTemplateCreate(@ModelAttribute List<OrderSaveReqDto> dtos) {

        // 방법3 : 스프링 시큐리티를 통한 주문 생성(토큰을 통한 사용자 인증), (getName = email)
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName(); // 중요 !!
        Ordering ordering = Ordering.builder()
                .memberEmail(memberEmail)
                .build();
        for (OrderSaveReqDto dto : dtos) {
            int quantity = dto.getProductCount();
            String productGetUrl = "http://product-service/product/"+dto.getProductId();
            HttpHeaders httpHeaders = new HttpHeaders();
            String token = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
            httpHeaders.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<CommonResDto> productEntity = restTemplate.exchange(productGetUrl, HttpMethod.GET, entity, CommonResDto.class);
            ObjectMapper objectMapper = new ObjectMapper();
            ProductDto productDto = objectMapper.convertValue(productEntity.getBody().getResult(), ProductDto.class);

            System.out.println(productDto);
            if (productDto.getName().contains("sale")) {
                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
                if(newQuantity<0){
                    throw new IllegalArgumentException("(redis) 재고가 부족합니다.");
                }
                stockDecreaseEventHandler.publish(new StockDecreaseEvent(productDto.getId(), dto.getProductCount()));

            } else {
                if (quantity > productDto.getStock_quantity()) {
                    throw new IllegalArgumentException("재고가 부족합니다");
                } else {
                    String updateUrl = "http://product-service/product/update/stock";
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);

                    HttpEntity<ProductUpdateStockDto> updateEntity =
                            new HttpEntity<>(ProductUpdateStockDto.builder()
                                    .productId(dto.getProductId())
                                    .productQuantity(dto.getProductCount())
                                    .build(), httpHeaders);

                    restTemplate.exchange(updateUrl, HttpMethod.PUT,  updateEntity, Void.class);
                }
            }
            System.out.println(productDto.getName());
            OrderDetail orderDetail = OrderDetail.builder()
                    .productId(productDto.getId())
                    .quantity(quantity)
                    .ordering(ordering)
//                    .productName(productDto.getName())
                    .build();
            ordering.getOrderDetails().add(orderDetail);
        }
        Ordering savedOreder = orderingRepository.save(ordering);

        sseController.publishMessage(savedOreder.fromEntity(), "admin@test.com");
        return savedOreder;
    }


    public Ordering orderFeignClientCreate(@ModelAttribute List<OrderSaveReqDto> dtos){
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName(); // 중요 !!
        Ordering ordering = Ordering.builder()
                .memberEmail(memberEmail)
                .build();
        for (OrderSaveReqDto dto : dtos) {
            int quantity = dto.getProductCount();
            // responseEntity 기본응답값이므로 바로 CommonResDto로 매핑
            CommonResDto commonResDto = productFeign.getProductById(dto.getProductId());

            ObjectMapper objectMapper = new ObjectMapper();
            ProductDto productDto = objectMapper.convertValue(commonResDto.getResult(), ProductDto.class);

            System.out.println(productDto);
            if (productDto.getName().contains("sale")) {
                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
                if(newQuantity<0){
                    throw new IllegalArgumentException("(redis) 재고가 부족합니다.");
                }
                stockDecreaseEventHandler.publish(new StockDecreaseEvent(productDto.getId(), dto.getProductCount()));

            } else {
                if (quantity > productDto.getStock_quantity()) {
                    throw new IllegalArgumentException("재고가 부족합니다");
                } else {
                    productFeign.updateProductStock(ProductUpdateStockDto.builder()
                            .productId(dto.getProductId())
                            .productQuantity(dto.getProductCount())
                            .build());
                }
            }
            System.out.println(productDto.getName());
            OrderDetail orderDetail = OrderDetail.builder()
                    .productId(productDto.getId())
                    .quantity(quantity)
                    .ordering(ordering)
//                    .productName(productDto.getName())
                    .build();


            ordering.getOrderDetails().add(orderDetail);
        }
        Ordering savedOreder = orderingRepository.save(ordering);

        sseController.publishMessage(savedOreder.fromEntity(), "admin@test.com");
        return savedOreder;
    }


//    public Ordering orderFeignKafkaCreate(@ModelAttribute List<OrderSaveReqDto> dtos){
//
//
//    }

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
