CREATE TABLE npc_dialogs (
    id     BIGSERIAL PRIMARY KEY,
    npc_id BIGINT NOT NULL REFERENCES npcs(id) ON DELETE CASCADE,
    nodes  JSONB  NOT NULL
);

CREATE UNIQUE INDEX idx_npc_dialogs_npc_id ON npc_dialogs(npc_id);
