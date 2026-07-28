package com.flowledger.commerce.customer.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commerce_customer_preferences")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCustomerPreferences extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    private String language = "en";
    private String theme = "light";
    private String currency = "INR";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_prefs", columnDefinition = "jsonb")
    private String notificationPrefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "communication_prefs", columnDefinition = "jsonb")
    private String communicationPrefs;
}
