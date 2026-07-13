package com.flowledger.common.service;

import com.flowledger.common.tenant.TenantContext;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public abstract class OrganizationScopedService {
    protected UUID orgId() {
        return TenantContext.getOrganizationId();
    }

    protected <T> T required(Optional<T> item, String type) {
        return item.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found"));
    }
}
