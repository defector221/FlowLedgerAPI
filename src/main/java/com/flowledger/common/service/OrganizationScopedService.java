package com.flowledger.common.service;

import com.flowledger.common.tenant.TenantContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;

public abstract class OrganizationScopedService {
    protected UUID orgId() {
        return TenantContext.getOrganizationId();
    }

    protected <T> T required(Optional<T> item, String type) {
        return item.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found"));
    }
}
