
To replicate...

Start local Zookeeper in Kafka dir:

    ./bin/zookeeper-server-start.sh config/zookeeper.properties
    
Start local Kafka in Kafka dir:

    ./bin/kafka-server-start.sh config/server.properties
    
Run system for 35 seconds which writes to first topic listed every second, then reads from it and writes to the other
one with exactly-once semantics (committing every 10s):
    
    ./gradlew run --no-daemon --args="source-topic target-topic 25"

This will forcefully kill the Kafka client meaning the messages will be uncommitted. Now run it again for 35 seconds:

    ./gradlew run --no-daemon --args="source-topic target-topic 25"

Now target-topic will have some aborted messages that should be skipped over. Run the Kafka consumer in Kafka dir with
`read_committed` to only see the ones properly committed:

    ./bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --from-beginning --topic target-topic --isolation-level=read_committed

Shows values like:

    2020-08-06T15:42:51.182Z
    2020-08-06T15:42:52.183Z
    2020-08-06T15:42:53.183Z
    2020-08-06T15:42:54.183Z
    2020-08-06T15:42:55.184Z
    2020-08-06T15:42:56.184Z
    2020-08-06T15:42:57.185Z
    2020-08-06T15:42:58.185Z
    2020-08-06T15:42:59.186Z
    2020-08-06T15:43:00.186Z
    2020-08-06T15:43:30.272Z
    2020-08-06T15:43:32.853Z
    2020-08-06T15:43:33.854Z
    2020-08-06T15:43:34.855Z
    2020-08-06T15:43:35.855Z
    2020-08-06T15:43:36.856Z
    2020-08-06T15:43:37.856Z
    2020-08-06T15:43:38.857Z

Now do the same w/ the built-in Go client that is also set to `read_committed`:

    go run . target-topic

Shows values like:

    2020-08-06T15:42:51.182Z
    2020-08-06T15:42:52.183Z
    2020-08-06T15:42:53.183Z
    2020-08-06T15:42:54.183Z
    2020-08-06T15:42:55.184Z
    2020-08-06T15:42:56.184Z
    2020-08-06T15:42:57.185Z
    2020-08-06T15:42:58.185Z
    2020-08-06T15:42:59.186Z
    2020-08-06T15:43:00.186Z
    2020-08-06T15:42:57.185Z
    2020-08-06T15:42:58.185Z
    2020-08-06T15:42:59.186Z
    2020-08-06T15:43:00.186Z
    2020-08-06T15:43:30.272Z
    2020-08-06T15:43:32.853Z
    2020-08-06T15:43:33.854Z
    2020-08-06T15:43:34.855Z
    2020-08-06T15:43:35.855Z
    2020-08-06T15:43:36.856Z
    2020-08-06T15:43:37.856Z
    2020-08-06T15:43:38.857Z
    
Notice how records between `2020-08-06T15:42:57.185Z` and `2020-08-06T15:43:00.186Z` are duplicated. Can dump the Kafka
logs:

    ./bin/kafka-dump-log.sh --files /tmp/kafka-logs/target-topic-0/00000000000000000000.log --print-data-log

Full dump:

    Dumping /tmp/kafka-logs/target-topic-0/00000000000000000000.log
    Starting offset: 0
    baseOffset: 0 lastOffset: 0 count: 1 baseSequence: 0 lastSequence: 0 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 0 CreateTime: 1596728571524 size: 107 magic: 2 compresscodec: LZ4 crc: 2238940677 isvalid: true
    | offset: 0 CreateTime: 1596728571524 keysize: -1 valuesize: 24 sequence: 0 headerKeys: [] payload: 2020-08-06T15:42:51.182Z
    baseOffset: 1 lastOffset: 1 count: 1 baseSequence: 1 lastSequence: 1 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 107 CreateTime: 1596728572387 size: 107 magic: 2 compresscodec: LZ4 crc: 1340030025 isvalid: true
    | offset: 1 CreateTime: 1596728572387 keysize: -1 valuesize: 24 sequence: 1 headerKeys: [] payload: 2020-08-06T15:42:52.183Z
    baseOffset: 2 lastOffset: 2 count: 1 baseSequence: 2 lastSequence: 2 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 214 CreateTime: 1596728573389 size: 107 magic: 2 compresscodec: LZ4 crc: 3585686424 isvalid: true
    | offset: 2 CreateTime: 1596728573389 keysize: -1 valuesize: 24 sequence: 2 headerKeys: [] payload: 2020-08-06T15:42:53.183Z
    baseOffset: 3 lastOffset: 3 count: 1 baseSequence: 3 lastSequence: 3 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 321 CreateTime: 1596728574387 size: 107 magic: 2 compresscodec: LZ4 crc: 1015816106 isvalid: true
    | offset: 3 CreateTime: 1596728574387 keysize: -1 valuesize: 24 sequence: 3 headerKeys: [] payload: 2020-08-06T15:42:54.183Z
    baseOffset: 4 lastOffset: 4 count: 1 baseSequence: 4 lastSequence: 4 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 428 CreateTime: 1596728575388 size: 107 magic: 2 compresscodec: LZ4 crc: 181466303 isvalid: true
    | offset: 4 CreateTime: 1596728575388 keysize: -1 valuesize: 24 sequence: 4 headerKeys: [] payload: 2020-08-06T15:42:55.184Z
    baseOffset: 5 lastOffset: 5 count: 1 baseSequence: 5 lastSequence: 5 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 535 CreateTime: 1596728576388 size: 107 magic: 2 compresscodec: LZ4 crc: 3621164138 isvalid: true
    | offset: 5 CreateTime: 1596728576388 keysize: -1 valuesize: 24 sequence: 5 headerKeys: [] payload: 2020-08-06T15:42:56.184Z
    baseOffset: 6 lastOffset: 6 count: 1 baseSequence: 0 lastSequence: 0 producerId: 4009 producerEpoch: 10 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 642 CreateTime: 1596728577915 size: 107 magic: 2 compresscodec: LZ4 crc: 2429438142 isvalid: true
    | offset: 6 CreateTime: 1596728577915 keysize: -1 valuesize: 24 sequence: 0 headerKeys: [] payload: 2020-08-06T15:42:57.185Z
    baseOffset: 7 lastOffset: 7 count: 1 baseSequence: -1 lastSequence: -1 producerId: 4005 producerEpoch: 16 partitionLeaderEpoch: 0 isTransactional: true isControl: true position: 749 CreateTime: 1596728578172 size: 78 magic: 2 compresscodec: NONE crc: 1861517873 isvalid: true
    | offset: 7 CreateTime: 1596728578172 keysize: 4 valuesize: 6 sequence: -1 headerKeys: [] endTxnMarker: COMMIT coordinatorEpoch: 0
    baseOffset: 8 lastOffset: 8 count: 1 baseSequence: 1 lastSequence: 1 producerId: 4009 producerEpoch: 10 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 827 CreateTime: 1596728578388 size: 107 magic: 2 compresscodec: LZ4 crc: 1273041392 isvalid: true
    | offset: 8 CreateTime: 1596728578388 keysize: -1 valuesize: 24 sequence: 1 headerKeys: [] payload: 2020-08-06T15:42:58.185Z
    baseOffset: 9 lastOffset: 9 count: 1 baseSequence: 2 lastSequence: 2 producerId: 4009 producerEpoch: 10 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 934 CreateTime: 1596728579390 size: 107 magic: 2 compresscodec: LZ4 crc: 719821753 isvalid: true
    | offset: 9 CreateTime: 1596728579390 keysize: -1 valuesize: 24 sequence: 2 headerKeys: [] payload: 2020-08-06T15:42:59.186Z
    baseOffset: 10 lastOffset: 10 count: 1 baseSequence: 3 lastSequence: 3 producerId: 4009 producerEpoch: 10 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1041 CreateTime: 1596728580390 size: 107 magic: 2 compresscodec: LZ4 crc: 3496072805 isvalid: true
    | offset: 10 CreateTime: 1596728580390 keysize: -1 valuesize: 24 sequence: 3 headerKeys: [] payload: 2020-08-06T15:43:00.186Z
    baseOffset: 11 lastOffset: 11 count: 1 baseSequence: -1 lastSequence: -1 producerId: 4009 producerEpoch: 11 partitionLeaderEpoch: 0 isTransactional: true isControl: true position: 1148 CreateTime: 1596728611892 size: 78 magic: 2 compresscodec: NONE crc: 157409123 isvalid: true
    | offset: 11 CreateTime: 1596728611892 keysize: 4 valuesize: 6 sequence: -1 headerKeys: [] endTxnMarker: ABORT coordinatorEpoch: 0
    baseOffset: 12 lastOffset: 17 count: 6 baseSequence: 0 lastSequence: 5 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1226 CreateTime: 1596728613269 size: 173 magic: 2 compresscodec: LZ4 crc: 2596930468 isvalid: true
    | offset: 12 CreateTime: 1596728613262 keysize: -1 valuesize: 24 sequence: 0 headerKeys: [] payload: 2020-08-06T15:42:57.185Z
    | offset: 13 CreateTime: 1596728613266 keysize: -1 valuesize: 24 sequence: 1 headerKeys: [] payload: 2020-08-06T15:42:58.185Z
    | offset: 14 CreateTime: 1596728613269 keysize: -1 valuesize: 24 sequence: 2 headerKeys: [] payload: 2020-08-06T15:42:59.186Z
    | offset: 15 CreateTime: 1596728613269 keysize: -1 valuesize: 24 sequence: 3 headerKeys: [] payload: 2020-08-06T15:43:00.186Z
    | offset: 16 CreateTime: 1596728613269 keysize: -1 valuesize: 24 sequence: 4 headerKeys: [] payload: 2020-08-06T15:43:30.272Z
    | offset: 17 CreateTime: 1596728613269 keysize: -1 valuesize: 24 sequence: 5 headerKeys: [] payload: 2020-08-06T15:43:32.853Z
    baseOffset: 18 lastOffset: 18 count: 1 baseSequence: 6 lastSequence: 6 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1399 CreateTime: 1596728614059 size: 107 magic: 2 compresscodec: LZ4 crc: 4200294917 isvalid: true
    | offset: 18 CreateTime: 1596728614059 keysize: -1 valuesize: 24 sequence: 6 headerKeys: [] payload: 2020-08-06T15:43:33.854Z
    baseOffset: 19 lastOffset: 19 count: 1 baseSequence: 7 lastSequence: 7 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1506 CreateTime: 1596728615059 size: 107 magic: 2 compresscodec: LZ4 crc: 1855645422 isvalid: true
    | offset: 19 CreateTime: 1596728615059 keysize: -1 valuesize: 24 sequence: 7 headerKeys: [] payload: 2020-08-06T15:43:34.855Z
    baseOffset: 20 lastOffset: 20 count: 1 baseSequence: 8 lastSequence: 8 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1613 CreateTime: 1596728616062 size: 107 magic: 2 compresscodec: LZ4 crc: 384338614 isvalid: true
    | offset: 20 CreateTime: 1596728616062 keysize: -1 valuesize: 24 sequence: 8 headerKeys: [] payload: 2020-08-06T15:43:35.855Z
    baseOffset: 21 lastOffset: 21 count: 1 baseSequence: 9 lastSequence: 9 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1720 CreateTime: 1596728617060 size: 107 magic: 2 compresscodec: LZ4 crc: 3642028945 isvalid: true
    | offset: 21 CreateTime: 1596728617060 keysize: -1 valuesize: 24 sequence: 9 headerKeys: [] payload: 2020-08-06T15:43:36.856Z
    baseOffset: 22 lastOffset: 22 count: 1 baseSequence: 10 lastSequence: 10 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1827 CreateTime: 1596728618063 size: 107 magic: 2 compresscodec: LZ4 crc: 1176497584 isvalid: true
    | offset: 22 CreateTime: 1596728618063 keysize: -1 valuesize: 24 sequence: 10 headerKeys: [] payload: 2020-08-06T15:43:37.856Z
    baseOffset: 23 lastOffset: 23 count: 1 baseSequence: 11 lastSequence: 11 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 1934 CreateTime: 1596728619063 size: 107 magic: 2 compresscodec: LZ4 crc: 3312652446 isvalid: true
    | offset: 23 CreateTime: 1596728619063 keysize: -1 valuesize: 24 sequence: 11 headerKeys: [] payload: 2020-08-06T15:43:38.857Z
    baseOffset: 24 lastOffset: 24 count: 1 baseSequence: 0 lastSequence: 0 producerId: 4009 producerEpoch: 13 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 2041 CreateTime: 1596728620477 size: 107 magic: 2 compresscodec: LZ4 crc: 2052001625 isvalid: true
    | offset: 24 CreateTime: 1596728620477 keysize: -1 valuesize: 24 sequence: 0 headerKeys: [] payload: 2020-08-06T15:43:39.857Z
    baseOffset: 25 lastOffset: 25 count: 1 baseSequence: -1 lastSequence: -1 producerId: 4005 producerEpoch: 18 partitionLeaderEpoch: 0 isTransactional: true isControl: true position: 2148 CreateTime: 1596728620722 size: 78 magic: 2 compresscodec: NONE crc: 284841599 isvalid: true
    | offset: 25 CreateTime: 1596728620722 keysize: 4 valuesize: 6 sequence: -1 headerKeys: [] endTxnMarker: COMMIT coordinatorEpoch: 0
    baseOffset: 26 lastOffset: 26 count: 1 baseSequence: 1 lastSequence: 1 producerId: 4009 producerEpoch: 13 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 2226 CreateTime: 1596728621062 size: 107 magic: 2 compresscodec: LZ4 crc: 3544688639 isvalid: true
    | offset: 26 CreateTime: 1596728621062 keysize: -1 valuesize: 24 sequence: 1 headerKeys: [] payload: 2020-08-06T15:43:40.858Z
    baseOffset: 27 lastOffset: 27 count: 1 baseSequence: 2 lastSequence: 2 producerId: 4009 producerEpoch: 13 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 2333 CreateTime: 1596728622064 size: 107 magic: 2 compresscodec: LZ4 crc: 2740170288 isvalid: true
    | offset: 27 CreateTime: 1596728622064 keysize: -1 valuesize: 24 sequence: 2 headerKeys: [] payload: 2020-08-06T15:43:41.858Z
    baseOffset: 28 lastOffset: 28 count: 1 baseSequence: 3 lastSequence: 3 producerId: 4009 producerEpoch: 13 partitionLeaderEpoch: 0 isTransactional: true isControl: false position: 2440 CreateTime: 1596728623066 size: 107 magic: 2 compresscodec: LZ4 crc: 1484506867 isvalid: true
    | offset: 28 CreateTime: 1596728623066 keysize: -1 valuesize: 24 sequence: 3 headerKeys: [] payload: 2020-08-06T15:43:42.859Z

The abort marker is present for the keys, but I don't know enough about Kafka formatting to know why.