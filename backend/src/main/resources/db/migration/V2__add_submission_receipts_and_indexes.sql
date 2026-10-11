-- The claimant/key uniqueness arbitrates concurrent submissions in the database, not in memory.
CREATE TABLE claim_submissions (
    claimant VARCHAR(255) NOT NULL,
    submission_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    claim_id UUID REFERENCES claims(id),
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (claimant, submission_key)
);

CREATE INDEX idx_claims_claimant_created ON claims(claimant, created_at);
CREATE INDEX idx_outbox_publication ON outbox_events(published_at, occurred_at);
