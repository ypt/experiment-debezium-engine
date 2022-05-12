-- set up replication permissions
-- https://debezium.io/documentation/reference/1.0/connectors/postgresql.html#PostgreSQL-permissions
CREATE ROLE name REPLICATION LOGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- schema1

CREATE SCHEMA schema1;

CREATE TABLE schema1.users (
  id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR
);

-- Optional: REPLICA IDENTITY FULL is required for previous value of the row to be available
-- https://debezium.io/documentation/reference/1.2/connectors/postgresql.html#postgresql-replica-identity
--ALTER TABLE schema1.users REPLICA IDENTITY FULL;

-- schema2

CREATE SCHEMA schema2;

CREATE TABLE schema2.users (
  id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR
);

-- https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html#options-for-applying-the-transformation-selectively
-- https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/
CREATE TABLE schema1.outbox (
  id uuid DEFAULT uuid_generate_v4(),
  aggregatetype VARCHAR(255),
  aggregateid VARCHAR(255),
  type VARCHAR(255),
  payload jsonb,
  PRIMARY KEY (id)
);

-- https://debezium.io/documentation/reference/stable/configuration/signalling.html#debezium-signaling-enabling-signaling
CREATE TABLE schema1.debezium_signal (
  id VARCHAR(42) PRIMARY KEY,
  type VARCHAR(32) NOT NULL,
  data VARCHAR(2048) NULL
);

-- Seed data
INSERT INTO schema1.users (full_name) VALUES ('susan smith');
INSERT INTO schema1.users (full_name) VALUES ('anne smith');
INSERT INTO schema2.users (full_name) VALUES ('bob smith');
INSERT INTO schema2.users (full_name) VALUES ('fred smith');