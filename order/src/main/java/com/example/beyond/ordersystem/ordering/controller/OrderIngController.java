package com.example.beyond.ordersystem.ordering.controller;

import com.example.beyond.ordersystem.common.dto.CommonResDto;
import com.example.beyond.ordersystem.ordering.domain.Ordering;
import com.example.beyond.ordersystem.ordering.dto.OrderListResDto;
import com.example.beyond.ordersystem.ordering.dto.OrderSaveReqDto;
import com.example.beyond.ordersystem.ordering.service.OrderingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("order")
public class OrderIngController {
    private final OrderingService orderingService;

    @Autowired
    public OrderIngController(OrderingService orderingService) {
        this.orderingService = orderingService;
    }

    @PostMapping("create")
    public ResponseEntity<?> orderCreate(@RequestBody List<OrderSaveReqDto> dto) {
        Ordering ordering = orderingService.orderCreate(dto);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.CREATED, "주문완료", ordering.getId());
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("list")
    public ResponseEntity<?> orderList() {
        List<OrderListResDto> orderList = orderingService.orderList();
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "주문 목록 조회", orderList);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    // 자신의 주문만 볼 수 있는 myOrder
    @GetMapping("myOrders")
    public ResponseEntity<?> myOrders(){
        List<OrderListResDto> dto = orderingService.myOrders();
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK,
                SecurityContextHolder.getContext().getAuthentication().getName()+"회원의 주문 목록을 조회합니다.",
                dto);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/cancel/{id}")
    public ResponseEntity<?> orderCancel(@PathVariable Long id){
        Ordering ordering = orderingService.orderCancel(id);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.CREATED, "주문이 취소되었습니다.", ordering.getId());
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }
}
