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
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedRecordCodecProviderTest {

    CodecProvider provider;

    @BeforeEach
    void beforeEach() {
       provider = new GeneratedRecordCodecProvider();
    }

    @Test
    void testSimpleRecord() {
        assertRoundTrip(
                SimpleRecord.class,
                new SimpleRecord("42", 1),
                new BsonDocument("id", new BsonString("42")).append("val", new BsonInt32(1)));
    }

    @Test
    void testAnnotatedRecord() {
         assertRoundTrip(AnnotatedRecord.class,
                 new AnnotatedRecord("Liz", 56, List.of("programming", "pottery"), "42"),
         new BsonDocument("name", new BsonString("Liz")).append("a", new BsonInt32(56))
                 .append("hobbies", new BsonArray(List.of(new BsonString("programming"), new BsonString("pottery"))))
                 .append("_id", new BsonString("42")));
    }

    private <T> void assertRoundTrip(Class<T> recordClass, T record, BsonDocument expectedDocument) {
        Codec<T> codec = provider.get(recordClass, DEFAULT_CODEC_REGISTRY);

        assertEquals(recordClass, codec.getEncoderClass());

        BsonDocument encodedDocument = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(encodedDocument);
        codec.encode(writer, record, EncoderContext.builder().build());

        assertEquals(expectedDocument, encodedDocument);

        BsonDocumentReader reader = new BsonDocumentReader(encodedDocument);
        T decodedRecord = codec.decode(reader, DecoderContext.builder().build());
        assertEquals(record, decodedRecord);
    }

}
