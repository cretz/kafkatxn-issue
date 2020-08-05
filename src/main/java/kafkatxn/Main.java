package kafkatxn;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.streaming.state.PredefinedOptions;
import org.apache.flink.contrib.streaming.state.RocksDBOptions;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.impl.SimpleLogger;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
  public static void main(String[] args) throws Exception {
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR");
    if (args.length != 3) {
      throw new IllegalArgumentException("Require 3 args: source topic, target topic, and time to wait until exit");
    }
    int seconds = Integer.parseInt(args[2]);
    System.out.println("Running for " + seconds + " seconds");
    CompletionService<?> service = new ExecutorCompletionService<>(Executors.newCachedThreadPool());
    // Run source gen then target gen
    service.submit(() -> runSourceGenerator(args[0]), null);
    service.submit(() -> runTargetProducer(args[0], args[1]), null);
    // Complete after sleep
    service.submit(() -> {
      try {
        Thread.sleep(seconds * 1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }, null);
    // Wait
    try {
      service.take().get();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    } finally {
      System.out.println("Exiting");
      System.exit(0);
    }
  }

  // Send a message once a second to a Kafka topic
  private static void runSourceGenerator(String topic) {
    // Create env
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.setParallelism(1);
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime);
    // Create source that emits the time once a second
    DataStreamSource<String> source = env.addSource(new SourceFunction<String>() {
      private final AtomicBoolean done = new AtomicBoolean();
      @Override
      public void run(SourceContext<String> ctx) throws Exception {
        while (!done.get()) {
          synchronized (ctx.getCheckpointLock()) {
            ctx.collect(Instant.now().toString());
          }
          Thread.sleep(1000);
        }
      }

      @Override
      public void cancel() {
        done.set(true);
      }
    });
    source.setParallelism(1);
    // Create producer sink
    Properties producerProps = new Properties();
    producerProps.setProperty("bootstrap.servers", "localhost:9092");
    producerProps.setProperty("security.protocol", "PLAINTEXT");
    producerProps.setProperty("batch.size", "512000");
    producerProps.setProperty("max.request.size", "1048576");
    producerProps.setProperty("linger.ms", "200");
    producerProps.setProperty("compression.type", "lz4");
    producerProps.setProperty("acks", "all");
    source.addSink(new FlinkKafkaProducer<>(topic, new SimpleStringSchema(), producerProps, null,
        FlinkKafkaProducer.Semantic.AT_LEAST_ONCE, FlinkKafkaProducer.DEFAULT_KAFKA_PRODUCERS_POOL_SIZE));
    // Run it
    try {
      env.execute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Receive kafka messages and write exactly-once to another Kafka topic
  private static void runTargetProducer(String sourceTopic, String targetTopic) {
    // Create env that checkpoints every 10 seconds
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.setParallelism(1);
    env.setStreamTimeCharacteristic(TimeCharacteristic.ProcessingTime);
    env.enableCheckpointing(10000);
    env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
    env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
    env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10000);
    // Setup rocksdb backend
    RocksDBStateBackend backend;
    try {
      backend = new RocksDBStateBackend(
          "file://" + Paths.get("kafkatxn-checkpoints").toAbsolutePath().toString(), false);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    backend.setPredefinedOptions(PredefinedOptions.SPINNING_DISK_OPTIMIZED_HIGH_MEM);
    Configuration backendConfig = new Configuration();
    backendConfig.setString(RocksDBOptions.TIMER_SERVICE_FACTORY.toString(),
        RocksDBStateBackend.PriorityQueueStateType.ROCKSDB.toString());
    backend = backend.configure(backendConfig, Thread.currentThread().getContextClassLoader());
    env.setStateBackend((StateBackend) backend);
    // Create source
    Properties consumerProps = new Properties();
    consumerProps.setProperty("bootstrap.servers", "localhost:9092");
    consumerProps.setProperty("security.protocol", "PLAINTEXT");
    consumerProps.setProperty("group.id", "kafkatxn");
    consumerProps.setProperty("auto.offset.reset", "latest");
    DataStreamSource<String> source = env.addSource(
        new FlinkKafkaConsumer<>(sourceTopic, new SimpleStringSchema(), consumerProps));
    // Create sink
    Properties producerProps = new Properties();
    producerProps.setProperty("bootstrap.servers", "localhost:9092");
    producerProps.setProperty("security.protocol", "PLAINTEXT");
    producerProps.setProperty("batch.size", "512000");
    producerProps.setProperty("max.request.size", "1048576");
    producerProps.setProperty("linger.ms", "200");
    producerProps.setProperty("compression.type", "lz4");
    producerProps.setProperty("acks", "all");
    producerProps.setProperty("transaction.timeout.ms", "360000");
    producerProps.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    source.addSink(new FlinkKafkaProducer<>(targetTopic, new SimpleStringSchema(), producerProps, null,
        FlinkKafkaProducer.Semantic.EXACTLY_ONCE, FlinkKafkaProducer.DEFAULT_KAFKA_PRODUCERS_POOL_SIZE));
    // Run it
    try {
      env.execute();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
