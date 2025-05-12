/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bson.codecs.record.codegen;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedRecordCodecProviderTest {

    CodecRegistry registry;

    @BeforeEach
    void beforeEach() {
        registry = CodecRegistries.fromProviders(
                DEFAULT_CODEC_REGISTRY,
                new GeneratedRecordCodecProvider());
    }

    @Test
    void testSimpleRecord() {
        assertRoundTrip(
                SimpleRecord.class,
                new SimpleRecord("42", 1),
                new BsonDocument("id", new BsonString("42")).append("val", new BsonInt32(1)));
    }


    @Test
    void testRecordWithPrimitives() {
        assertRoundTrip(
                TestRecordWithPrimitives.class,
                new TestRecordWithPrimitives(42),
                new BsonDocument("i", new BsonInt32(42)));
    }

    @Test
    void testAnnotatedRecord() {
        assertRoundTrip(AnnotatedRecord.class,
                new AnnotatedRecord("Liz", 56, List.of("programming", "pottery"), "42"),
                new BsonDocument("name", new BsonString("Liz")).append("a", new BsonInt32(56))
                        .append("hobbies", new BsonArray(List.of(new BsonString("programming"), new BsonString("pottery"))))
                        .append("_id", new BsonString("42")));
    }

    @Test
    void testParameterizedRecord() {
        ObjectId id = new ObjectId();
        assertRoundTrip(TestRecordWithParameterizedRecord.class,
                new TestRecordWithParameterizedRecord(id,
                        new TestRecordParameterized<>(42.0, List.of(new TestRecordEmbedded("n1")))),
                new BsonDocument("_id", new BsonObjectId(id))
                        .append("parameterizedRecord",
                                new BsonDocument("number", new BsonDouble(42.0))
                                        .append("parameterizedList",
                                                new BsonArray(List.of(
                                                        new BsonDocument("name", new BsonString("n1")))))));
    }

    private <T> void assertRoundTrip(Class<T> recordClass, T record, BsonDocument expectedDocument) {
        Codec<T> codec = registry.get(recordClass);

        assertEquals(recordClass, codec.getEncoderClass());

        BsonDocument encodedDocument = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(encodedDocument);
        codec.encode(writer, record, EncoderContext.builder().build());

        assertEquals(expectedDocument, encodedDocument);

        BsonDocumentReader reader = new BsonDocumentReader(expectedDocument);
        T decodedRecord = codec.decode(reader, DecoderContext.builder().build());
        assertEquals(record, decodedRecord);
    }

}
