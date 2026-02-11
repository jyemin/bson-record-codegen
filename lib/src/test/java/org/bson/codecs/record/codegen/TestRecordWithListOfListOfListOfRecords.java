package org.bson.codecs.record.codegen;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.List;

public record TestRecordWithListOfListOfListOfRecords(@BsonId ObjectId id, List<List<List<TestRecordEmbedded>>> nestedRecords) {
}

