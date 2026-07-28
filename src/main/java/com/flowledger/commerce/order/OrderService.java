package com.flowledger.commerce.order;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final CommerceOrderRepository orders;
    private final OrderMapper mapper;

    public OrderService(CommerceOrderRepository orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    public PageResponse<CommerceDtos.CommerceOrderResponse> listOrders(Pageable pageable) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        Page<CommerceOrder> page = orders.findByCustomerIdOrderByPlacedAtDesc(customerId, pageable);
        return PageResponse.from(page.map(mapper::toOrderResponse));
    }

    public CommerceDtos.CommerceOrderResponse getOrder(UUID orderId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceOrder order = orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapper.toOrderResponse(order);
    }
}
