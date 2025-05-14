package org.bson.codecs.record.codegen;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.record.RecordCodecProvider;
import org.bson.codecs.record.records.SimpleRecordWithPrimitives;
import org.bson.io.BasicOutputBuffer;
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
import java.util.concurrent.TimeUnit;

import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
public class GeneratedRecordCodecBenchmark {

    @State(Scope.Benchmark)
    public static class Input {
        private SimpleRecordWithPrimitives simpleRecord;
        private Document simpleDocument;
        private BsonDocument simpleBsonDocument;
        private Codec<SimpleRecordWithPrimitives> generatedRecordCodec;
        private Codec<SimpleRecordWithPrimitives> reflectiveRecordCodec;
        private Codec<Document> documentCodec;
        private Codec<BsonDocument> bsonDocumentCodec;
        private byte[] documentBytes;
        private BsonBinaryReader reader;
        private BsonBinaryWriter writer;

        @Setup
        public void setup() {
            generatedRecordCodec = CodecRegistries.fromProviders(
                    DEFAULT_CODEC_REGISTRY, new GeneratedRecordCodecProvider())
                    .get(SimpleRecordWithPrimitives.class);

            reflectiveRecordCodec = CodecRegistries.fromProviders(
                    DEFAULT_CODEC_REGISTRY, new RecordCodecProvider())
                    .get(SimpleRecordWithPrimitives.class);

            documentCodec = DEFAULT_CODEC_REGISTRY.get(Document.class);
            bsonDocumentCodec = DEFAULT_CODEC_REGISTRY.get(BsonDocument.class);

            simpleRecord = new SimpleRecordWithPrimitives(1, 2, 3, 4);
            simpleDocument = new Document("i1", 1).append("i2", 2).append("l1", 3L).append("l2", 4L);
            simpleBsonDocument = new BsonDocument("i1", new BsonInt32(1)).append("i2", new BsonInt32(2))
                    .append("l1", new BsonInt64(3)).append("l2", new BsonInt64(4));

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

    @Benchmark
    public void encodeWithDocument(Input input, Blackhole blackhole) {
        input.documentCodec.encode(input.writer, input.simpleDocument, EncoderContext.builder().build());
        blackhole.consume(input);
    }

    @Benchmark
    public void decodeWithDocument(Input input, Blackhole blackhole) {
        blackhole.consume(input.documentCodec.decode(input.reader, DecoderContext.builder().build()));
    }

    @Benchmark
    public void encodeWithBsonDocument(Input input, Blackhole blackhole) {
        input.bsonDocumentCodec.encode(input.writer, input.simpleBsonDocument, EncoderContext.builder().build());
        blackhole.consume(input);
    }

    @Benchmark
    public void decodeWithBsonDocument(Input input, Blackhole blackhole) {
        blackhole.consume(input.bsonDocumentCodec.decode(input.reader, DecoderContext.builder().build()));
    }

}
