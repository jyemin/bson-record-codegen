package org.bson.codecs.record.codegen;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Map;

public record TestRecordWithMapOfMapOfRecords(@BsonId ObjectId id, Map<String, Map<String, TestRecordEmbedded>> nestedRecords) {
}

