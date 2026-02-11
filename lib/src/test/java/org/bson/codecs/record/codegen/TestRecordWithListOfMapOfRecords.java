package org.bson.codecs.record.codegen;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public record TestRecordWithListOfMapOfRecords(@BsonId ObjectId id, List<Map<String, TestRecordEmbedded>> nestedRecords) {
}

