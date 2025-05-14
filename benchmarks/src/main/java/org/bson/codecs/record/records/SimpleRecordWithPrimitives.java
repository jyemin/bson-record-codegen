package org.bson.codecs.record.records;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.List;

public record SimpleRecordWithPrimitives(int i1, int i2, long l1, long l2) {
}
