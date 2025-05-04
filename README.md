# bson-record-codegen

A CodecProvider for the MongoDB BSON library that generates Codec classes at runtime for instances of 
`java.lang.Record`. The intention is for it to have equivalent semantics to the existing support for 
record codecs in the BSON library, but without the runtime overhead of reflection.

It utilizes the Class-File API introduced in Java 24 in scope of JEP 484 (https://openjdk.org/jeps/484).


