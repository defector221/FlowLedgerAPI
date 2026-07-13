package com.flowledger.organization.dto;
public record UpdateOrganizationRequest(String name,String legalName,String gstin,String pan,String email,String phone,String website,String billingAddress,String shippingAddress,String city,String state,String stateCode,String financialYearStart,String invoicePrefix) {}
