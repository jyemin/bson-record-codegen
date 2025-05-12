package org.bson.codecs.record.codegen;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.record.RecordCodecProvider;
import org.bson.codecs.record.records.SimpleRecord;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(0)
public class GeneratedRecordCodecBenchmark {

    @State(Scope.Benchmark)
    public static class Input {
        private SimpleRecord simpleRecord;
        private Codec<SimpleRecord> generatedRecordCodec;
        private Codec<SimpleRecord> reflectiveRecordCodec;
        private byte[] documentBytes;
        private BsonBinaryReader reader;
        private BsonBinaryWriter writer;

        @Setup
        public void setup() {
            generatedRecordCodec = CodecRegistries.fromProviders(
                    DEFAULT_CODEC_REGISTRY, new GeneratedRecordCodecProvider())
                    .get(SimpleRecord.class);

            reflectiveRecordCodec = CodecRegistries.fromProviders(
                    DEFAULT_CODEC_REGISTRY, new RecordCodecProvider())
                    .get(SimpleRecord.class);

            simpleRecord = new SimpleRecord(ObjectId.get(), "Adrian", 42, List.of("viola", "soccer"));

            BasicOutputBuffer buffer = new BasicOutputBuffer();
            reflectiveRecordCodec.encode(new BsonBinaryWriter(buffer), simpleRecord, EncoderContext.builder().build());
            documentBytes = buffer.toByteArray();
        }

        @Setup(Level.Invocation)
        public void beforeIteration() {
            writer = new BsonBinaryWriter(new BasicOutputBuffer(256));
            reader = new BsonBinaryReader(ByteBuffer.wrap(documentBytes));
        }
    }

    @Benchmark
    public void encodeWithGenerated(Input input, Blackhole blackhole) {
        input.generatedRecordCodec.encode(input.writer, input.simpleRecord, EncoderContext.builder().build());
        blackhole.consume(input);
    }

    @Benchmark
    public void decodeWithGenerated(Input input, Blackhole blackhole) {
        blackhole.consume(input.generatedRecordCodec.decode(input.reader, DecoderContext.builder().build()));
    }

    @Benchmark
    public void encodeWithReflective(Input input, Blackhole blackhole) {
        input.reflectiveRecordCodec.encode(input.writer, input.simpleRecord, EncoderContext.builder().build());
        blackhole.consume(input);
    }

    @Benchmark
    public void decodeWithReflective(Input input, Blackhole blackhole) {
        blackhole.consume(input.reflectiveRecordCodec.decode(input.reader, DecoderContext.builder().build()));
    }

}
