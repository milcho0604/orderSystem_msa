package com.example.beyond.ordersystem.ordering.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockDecreaseEvent {
    private Long productId;
    private Integer productCount;
}
