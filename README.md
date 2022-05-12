# experiment-debezium-engine

An experiment with embedding [Debezium Engine](https://debezium.io/documentation/reference/stable/development/engine.html)
(the library) directly in an app.

Alternate ways to run Debezium include [Debezium Server](https://debezium.io/documentation/reference/stable/operations/debezium-server.html),
[Kafka Connect](https://debezium.io/documentation/reference/stable/tutorial.html#introduction-debezium), 
[Pulsar IO](https://pulsar.apache.org/docs/next/io-cdc), [Flink](https://github.com/ververica/flink-cdc-connectors), and more. 

## A Hands-on Example

Start Postgres
```shell
docker-compose up
```

Build the project
```shell
./gradlew build
```

Run a simple example using the configuration from the `example-simple-table-cdc.properties` file 
```shell
java -jar build/libs/experiment-debezium-engine-1.0-SNAPSHOT.jar example-simple-table-cdc.properties
```

Exec into Postgres
```shell
docker-compose exec db psql experiment experiment
```

Insert some data into the database
```sql
-- in db container

INSERT INTO schema1.users (full_name) VALUES ('kate smith');
```

The Debezium process will print something similar to the following
```
[
    EmbeddedEngineChangeEvent [
        key=null, 
        value=
            SourceRecord{
                sourcePartition={server=experiment}, 
                sourceOffset={transaction_id=null, lsn_proc=23594904, lsn_commit=23584544, lsn=23594904, txId=570, ts_usec=1652366692822782}
            }
            ConnectRecord{
                topic='experiment.schema1.users', 
                kafkaPartition=null, 
                key=Struct{id=3},
                keySchema=Schema{experiment.schema1.users.Key:STRUCT},
                value=Struct{
                    after=Struct{
                        id=3,
                        full_name=kate smith
                    },
                    source=Struct{
                        version=1.9.2.Final,
                        connector=postgresql,
                        name=experiment,
                        ts_ms=1652366692822,
                        db=experiment,
                        sequence=["23584544","23594904"],
                        schema=schema1,
                        table=users,
                        txId=570,
                        lsn=23594904
                    },
                    op=c,
                    ts_ms=1652366694164
                },
                valueSchema=Schema{experiment.schema1.users.Envelope:STRUCT}, 
                timestamp=null,
                headers=ConnectHeaders(headers=)
            },
        sourceRecord=
            SourceRecord{
                sourcePartition={server=experiment},
                sourceOffset={transaction_id=null, lsn_proc=23594904, lsn_commit=23584544, lsn=23594904, txId=570, ts_usec=1652366692822782}
            } 
            ConnectRecord{
                topic='experiment.schema1.users', 
                kafkaPartition=null,
                key=Struct{id=3}, 
                keySchema=Schema{experiment.schema1.users.Key:STRUCT}, 
                value=Struct{
                    after=Struct{
                        id=3,
                        full_name=kate smith
                    },
                    source=Struct{
                        version=1.9.2.Final,
                        connector=postgresql,
                        name=experiment,
                        ts_ms=1652366692822,
                        db=experiment,
                        sequence=["23584544","23594904"],
                        schema=schema1,
                        table=users,
                        txId=570,
                        lsn=23594904
                    },
                    op=c,
                    ts_ms=1652366694164
                }, 
                valueSchema=Schema{experiment.schema1.users.Envelope:STRUCT},
                timestamp=null, 
                headers=ConnectHeaders(headers=)
            }
    ]
]
```

The Debezium process should have created a replication slot on Postgres
```sql
-- in db container

SELECT * from pg_replication_slots;

--             slot_name             |  plugin  | slot_type | datoid |  database  | temporary | active | active_pid | xmin | catalog_xmin | restart_lsn | confirmed_flush_lsn 
-- ----------------------------------+----------+-----------+--------+------------+-----------+--------+------------+------+--------------+-------------+---------------------
--  experimentdebeziumenginetablecdc | pgoutput | logical   |  16384 | experiment | f         | t      |        103 |      |          570 | 0/16806F8   | 0/16806F8
-- (1 row)
```

## Data Backfills & Snapshots
What if we also want to deliver pre-existing data for backfills?

No problem, we can [trigger ad-hoc delivery of backfill snapshot data ad-hoc](https://debezium.io/documentation/reference/stable/connectors/postgresql.html#postgresql-ad-hoc-snapshots).
```sql
-- In db container
INSERT INTO schema1.debezium_signal (id, type, data) 
VALUES('ad-hoc-1', 'execute-snapshot', '{"data-collections": ["schema1.users", "schema2.users"],"type":"incremental"}');
```

Now you should see the preexisting rows print out.

Alternatively, backfill snapshots can be configured to be delivered in [different scenarios](https://debezium.io/documentation/reference/stable/connectors/postgresql.html#postgresql-snapshots)

## Transformations (SMT's)

For plugging in simple transformations, Debezium Engine supports [Single Message Transformations (SMT)](https://debezium.io/documentation/reference/stable/development/engine.html#engine-message-transformations)
and [provides several](https://debezium.io/documentation/reference/stable/transformations/index.html) out of the box.

## Transformation Example: Logical Table Router SMT

For example, the [Logical Table Router](https://debezium.io/documentation/reference/stable/transformations/topic-routing.html)
might be useful for merging multiple physical shards into a single logical table. 

e.g. merging `db1.schema1.users` and `db1.schema2.users` tables into a single `all_dbs.all_shards.users` logical table.

Restart the Debezium process using the configuration in the `example-transform-merge-shards-into-logical-table.properties`,
file which enables the `io.debezium.transforms.ByLogicalTableRouter` transformation.

```shell
java -jar build/libs/experiment-debezium-engine-1.0-SNAPSHOT.jar example-transform-merge-shards-into-logical-table.properties
```

Insert some data into the database
```sql
-- in db container

INSERT INTO schema1.users (full_name) VALUES ('kate smith');
```

The Debezium process will print something similar to the following. Note how this affects  `topic`, `key`.
```
[
    EmbeddedEngineChangeEvent [
        key=null, 
        value=
            SourceRecord{
                sourcePartition={server=experiment}, 
                sourceOffset={transaction_id=null, lsn_proc=23599552, lsn_commit=23599392, lsn=23599552, txId=584, ts_usec=1652367418051690}
            } 
            ConnectRecord{
                topic='persistent://public/default/all_dbs.all_shards.users', 
                kafkaPartition=null, 
                key=Struct{id=4,__dbz__physicalTableIdentifier=experiment.schema1.users}, 
                keySchema=Schema{persistent___public_default_all_dbs.all_shards.users.Key:STRUCT}, 
                value=Struct{
                    after=Struct{
                        id=4,
                        full_name=kate smith
                    },
                    source=Struct{
                        version=1.9.2.Final,
                        connector=postgresql,
                        name=experiment,
                        ts_ms=1652367418051,
                        db=experiment,
                        sequence=["23599392","23599552"],
                        schema=schema1,
                        table=users,
                        txId=584,
                        lsn=23599552
                    },
                    op=c,
                    ts_ms=1652367418131
                }, 
                valueSchema=Schema{persistent___public_default_all_dbs.all_shards.users.Envelope:STRUCT}, 
                timestamp=null, 
                headers=ConnectHeaders(headers=)
            }, 
        sourceRecord=
            SourceRecord{
                sourcePartition={server=experiment}, 
                sourceOffset={transaction_id=null, lsn_proc=23599552, lsn_commit=23599392, lsn=23599552, txId=584, ts_usec=1652367418051690}} 
            ConnectRecord{
                topic='persistent://public/default/all_dbs.all_shards.users', 
                kafkaPartition=null, 
                key=Struct{id=4,__dbz__physicalTableIdentifier=experiment.schema1.users}, 
                keySchema=Schema{persistent___public_default_all_dbs.all_shards.users.Key:STRUCT}, 
                value=Struct{
                    after=Struct{
                        id=4,
                        full_name=kate smith
                    },
                    source=Struct{
                        version=1.9.2.Final,
                        connector=postgresql,
                        name=experiment,
                        ts_ms=1652367418051,
                        db=experiment,
                        sequence=["23599392","23599552"],
                        schema=schema1,
                        table=users,
                        txId=584,
                        lsn=23599552
                    },
                    op=c,
                    ts_ms=1652367418131
                },
                valueSchema=Schema{persistent___public_default_all_dbs.all_shards.users.Envelope:STRUCT},
                timestamp=null, 
                headers=ConnectHeaders(headers=)
            }
    ]
]
```

## Transformation Example: Outbox SMT

Now let's try a different configuration of Debezium Server, this time [configured](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
as a [transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html) router. It will route
payloads written to the `outbox` table in the database directly to the specified topic.

Restart the Debezium process using the configuration in the `example-transform-outbox.properties` file,
which enables the `io.debezium.transforms.outbox.EventRouter` transformation.
```shell
java -jar build/libs/experiment-debezium-engine-1.0-SNAPSHOT.jar example-transform-outbox.properties
```

Write to the `outbox` table
```sql
-- in db container

INSERT INTO schema1.outbox (aggregatetype, aggregateid, type, payload) 
VALUES (
  'persistent://public/default/aggregatetype', 
  'aggregateid', 
  'type', 
  '{"hello":"world"}'::jsonb
 );
```

The Debezium process will print something similar to the following. Note how this affects `topic`, `key`, `value`.
```
[
    EmbeddedEngineChangeEvent [
        key=null, 
        value=
            SourceRecord{
                sourcePartition={server=experiment}, 
                sourceOffset={transaction_id=null, lsn_proc=23600368, lsn_commit=23600088, lsn=23600368, txId=585, ts_usec=1652368021450577}
            }
            ConnectRecord{
                topic='${routedByValue}', 
                kafkaPartition=null, 
                key=aggregateid, 
                keySchema=Schema{STRING}, 
                value=Struct{
                    hello=world
                }, 
                valueSchema=Schema{STRUCT}, 
                timestamp=1652368021588, 
                headers=ConnectHeaders(headers=[ConnectHeader(key=id, value=04806fc9-bb75-4a6d-a164-3a532f09ba31, schema=Schema{io.debezium.data.Uuid:STRING})])
            }, 
        sourceRecord=
            SourceRecord{
                sourcePartition={server=experiment}, 
                sourceOffset={transaction_id=null, lsn_proc=23600368, lsn_commit=23600088, lsn=23600368, txId=585, ts_usec=1652368021450577}
            }
            ConnectRecord{
                topic='${routedByValue}', 
                kafkaPartition=null, 
                key=aggregateid, 
                keySchema=Schema{STRING}, 
                value=Struct{
                    hello=world
                }, 
                valueSchema=Schema{STRUCT}, 
                timestamp=1652368021588, 
                headers=ConnectHeaders(headers=[ConnectHeader(key=id, value=04806fc9-bb75-4a6d-a164-3a532f09ba31, schema=Schema{io.debezium.data.Uuid:STRING})])
            }
    ]
]
```
