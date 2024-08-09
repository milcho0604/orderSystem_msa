package com.example.beyond.ordersystem.ordering.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockDecreaseEvent {
    private Long productId;
    private Integer productCount;
}
