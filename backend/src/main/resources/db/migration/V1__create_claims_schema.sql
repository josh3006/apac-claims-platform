-- Initial schema corresponding to the original Hibernate-managed application.
-- This version is baselined (not executed) when explicitly adopting a reviewed legacy database.
CREATE TABLE claims (
    id UUID PRIMARY KEY,
    version BIGINT,
    claimant VARCHAR(255) NOT NULL,
    type VARCHAR(255),
    market VARCHAR(255),
    currency VARCHAR(255),
    incident_date DATE,
    description VARCHAR(2000),
    estimated_amount NUMERIC(19, 2),
    settlement_amount NUMERIC(19, 2),
    status VARCHAR(255),
    assigned_officer VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE claim_history (
    claim_id UUID NOT NULL REFERENCES claims(id),
    sequence_number INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE,
    actor VARCHAR(255),
    status VARCHAR(255),
    message VARCHAR(2000),
    PRIMARY KEY (claim_id, sequence_number)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    claim_id UUID,
    claim_version BIGINT NOT NULL,
    status VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE
);
