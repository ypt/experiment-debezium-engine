package com.example.experimentdebeziumengine;

import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import org.apache.kafka.connect.source.SourceRecord;

import java.util.List;

// https://debezium.io/documentation/reference/stable/development/engine.html#advanced-consuming
class MyChangeConsumer implements DebeziumEngine.ChangeConsumer<RecordChangeEvent<SourceRecord>> {
    public void handleBatch(List<RecordChangeEvent<SourceRecord>> records, DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer) throws InterruptedException {
        System.out.println(records);
    }
}