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

import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.List;

public record AnnotatedRecord(String name,
                              @BsonProperty("a") Integer age,
                              List<String> hobbies,
                              @BsonRepresentation(BsonType.OBJECT_ID) @BsonId String identifier) {

    public AnnotatedRecord(final String name, final Integer age, final List<String> hobbies, final String identifier) {
        this.name = name;
        this.age = age;
        this.hobbies = hobbies;
        this.identifier = identifier;
    }
}
