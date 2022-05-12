package com.example.experimentdebeziumengine;

import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import org.apache.kafka.connect.source.SourceRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        Properties props = null;
        String propsFileName = args[0];
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream(propsFileName)) {
            if (input == null) {
                System.out.println("Unable to find " + propsFileName);
                return;
            }
            props = new Properties();
            props.load(input);

            System.out.println(props);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (props == null) {
            return;
        }

        // try (DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> engine = DebeziumEngine.create(Connect.class)
        try (DebeziumEngine<RecordChangeEvent<SourceRecord>> engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(props)
                // .notifying(record -> {
                //     System.out.println(record);
                // })
                .notifying(new MyChangeConsumer())
                .build()
        ) {
            // Run the engine asynchronously ...
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(engine);

            // Do something else or wait for a signal or an event
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        // Engine is stopped when the main code is finished
    }

    // TODO: gracefully shut down
    //  https://debezium.io/documentation/reference/stable/development/engine.html#:~:text=Your%20application%20can%20stop%20the%20engine%20safely%20and%20gracefully
}