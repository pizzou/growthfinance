package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import lombok.Getter;

/**
 * The "principal" set on the SecurityContext when a request is authenticated via an
 * X-Api-Key belonging to an external regulatory/credit-bureau system, rather than a
 * staff JWT. Controllers pull the org + client type off this instead of a User.
 */
@Getter
public class RegulatoryApiPrincipal {
    private final Long clientId;
    private final Long organizationId;
    private final String clientName;
    private final RegulatoryApiClient.ClientType clientType;

    public RegulatoryApiPrincipal(RegulatoryApiClient client) {
        this.clientId = client.getId();
        this.organizationId = client.getOrganization().getId();
        this.clientName = client.getName();
        this.clientType = client.getClientType();
    }
}