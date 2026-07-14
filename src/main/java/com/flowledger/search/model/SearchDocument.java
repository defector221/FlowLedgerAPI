package com.flowledger.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {
    private String documentId;
    private String organizationId;
    private String entityId;
    private String entityType;
    private String title;
    private String subtitle;
    private String searchText;
    private String referenceNumber;
    private String status;
    /** ISO-8601 instant; String avoids OpenSearch date mapper rejecting Instant objects. */
    private String updatedAt;
}
