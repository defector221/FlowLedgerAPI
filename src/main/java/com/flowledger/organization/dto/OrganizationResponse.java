package com.flowledger.organization.dto;
import java.util.UUID; public record OrganizationResponse(UUID id,String name,String legalName,String logoObjectKey,String email,String phone,String gstin,String financialYearStart,String invoicePrefix) {}
