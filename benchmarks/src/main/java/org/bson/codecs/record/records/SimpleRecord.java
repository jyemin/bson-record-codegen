package org.bson.codecs.record.records;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.List;

public record SimpleRecord(@BsonId ObjectId id,
                           String name,
                           @BsonProperty("a") Integer age,
                           List<String> hobbies) {
}
