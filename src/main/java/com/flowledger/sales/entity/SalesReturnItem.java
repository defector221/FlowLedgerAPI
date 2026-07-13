package com.flowledger.sales.entity;
import jakarta.persistence.*;import lombok.*;import java.math.*;import java.util.*;
@Entity @Table(name="sales_return_items") @Getter @Setter @NoArgsConstructor public class SalesReturnItem {@Id @GeneratedValue(strategy=GenerationType.UUID)private UUID id;@ManyToOne(fetch=FetchType.LAZY)@JoinColumn(name="sales_return_id")private SalesReturn salesReturn;@Column(nullable=false)private UUID productId;@Column(nullable=false)private BigDecimal quantity,rate;@Column(nullable=false)private BigDecimal lineTotal=BigDecimal.ZERO;private int lineOrder;}
