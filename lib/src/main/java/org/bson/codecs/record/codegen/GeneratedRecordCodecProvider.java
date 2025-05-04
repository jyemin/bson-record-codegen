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

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.Decoder;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.Encoder;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static org.bson.assertions.Assertions.assertNotNull;

public class GeneratedRecordCodecProvider implements CodecProvider {
    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (!assertNotNull(clazz).isRecord()) {
            return null;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Codec<T> result = new RecordCodecGenerator(clazz, registry).generateCodec();
        return result;
    }

    public static class RecordCodecGenerator<T extends Record> {
        private static final ClassDesc codecClassDesc = ClassDesc.of(Codec.class.getName());
        private static final ClassDesc bsonTypeClassDesc = ClassDesc.of(BsonType.class.getName());
        private static final ClassDesc bsonWriterClassDesc = ClassDesc.of(BsonWriter.class.getName());
        private static final ClassDesc encoderContextClassDesc = ClassDesc.of(EncoderContext.class.getName());
        private static final ClassDesc encoderClassDesc = ClassDesc.of(Encoder.class.getName());
        private static final ClassDesc bsonReaderClassDesc = ClassDesc.of(BsonReader.class.getName());
        private static final ClassDesc decoderContextClassDesc = ClassDesc.of(DecoderContext.class.getName());
        private static final ClassDesc decoderClassDesc = ClassDesc.of(Decoder.class.getName());

        private static final int thisSlot = 0;

        private final Class<T> recordClass;
        private final ClassDesc recordClassDesc;
        private final ClassDesc recordCodecClassDesc;
        private final CodecRegistry registry;

        public RecordCodecGenerator(Class<T> recordClass, CodecRegistry registry) {
            this.recordClass = recordClass;
            this.recordClassDesc = ClassDesc.of(recordClass.getName());
            this.recordCodecClassDesc = ClassDesc.of("org.bson.codecs.record", recordClass.getSimpleName() + "Codec");
            this.registry = registry;
        }

        public Codec<T> generateCodec() {
            var bytes = generateClass();

            var loader = new ByteArrayClassLoader();
            var clazz = loader.defineClass(null, bytes);

            try {
                //noinspection unchecked
                return (Codec<T>) clazz.getDeclaredConstructor(CodecRegistry.class).newInstance(registry);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] generateClass() {
            return ClassFile.of().build(
                    recordCodecClassDesc,
                    clb -> {
                        clb
                                .withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER)
                                .withInterfaceSymbols(ClassDesc.of(Codec.class.getName()));
                        generateFields(clb);
                        generateConstructor(clb);
                        generateGetEncoderClassMethod(clb);
                        generateEncodeMethod(clb);
                        generateDecodeMethod(clb);
                    });
        }

        private void generateFields(ClassBuilder clb) {
            var recordComponents = recordClass.getRecordComponents();
            for (var recordComponent : recordComponents) {
                clb.withField(recordComponent.getName() + "Codec",
                        ClassDesc.of("org.bson.codecs", "Codec"), ACC_PRIVATE | ACC_FINAL);
            }
        }

        private void generateGetEncoderClassMethod(ClassBuilder clb) {
            clb.withMethodBody("getEncoderClass", MethodTypeDesc.of(CD_Class), ACC_PUBLIC,
                    cob -> cob.
                            ldc(clb.constantPool().classEntry(ClassDesc.of(recordClass.getName())))
                            .return_(TypeKind.REFERENCE));
        }

        private void generateConstructor(ClassBuilder clb) {
            var codecClassDesc = ClassDesc.of(Codec.class.getName());
            var codecRegistryClassDesc = ClassDesc.of(CodecRegistry.class.getName());
            clb.withMethodBody(ConstantDescs.INIT_NAME,
                    MethodTypeDesc.of(CD_void, codecRegistryClassDesc),
                    ACC_PUBLIC,
                    cob -> {
                        cob
                                .aload(0)
                                .invokespecial(ConstantDescs.CD_Object,
                                        ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);

                        for (var recordComponent : recordClass.getRecordComponents()) {
                            cob
                                    .aload(0)
                                    .aload(1)
                                    .ldc(ClassDesc.of(recordComponent.getType().getName()))
                                    .invokeinterface(codecRegistryClassDesc, "get", MethodTypeDesc.of(codecClassDesc, CD_Class))
                                    .putfield(recordCodecClassDesc,
                                            recordComponent.getName() + "Codec",
                                            ClassDesc.of(Codec.class.getName()));

                        }
                        cob.return_();
                    });
        }

        private void generateEncodeMethod(ClassBuilder clb) {
            var methodTypeDesc = MethodTypeDesc.of(CD_void, bsonWriterClassDesc, recordClassDesc, encoderContextClassDesc);
            int writerSlot = 1;
            int recordClassSlot = 2;
            int encoderContextSlot = 3;
            clb.withMethodBody("encode",
                    methodTypeDesc,
                    ACC_PUBLIC,
                    cob -> {
                        cob
                                .aload(writerSlot)
                                .invokeinterface(bsonWriterClassDesc, "writeStartDocument", MethodTypeDesc.of(CD_void));

                        for (var recordComponent : recordClass.getRecordComponents()) {
                            var l0 = cob.newLabel();
                            var name = recordComponent.getName();
                            var recordComponentMtd = MethodTypeDesc.of(ClassDesc.of(recordComponent.getType().getName()));

                            cob
                                    .aload(recordClassSlot)
                                    .invokevirtual(recordClassDesc, name, recordComponentMtd);

                            cob
                                    .ifnull(l0)
                                    .aload(writerSlot)
                                    .ldc(clb.constantPool().stringEntry(name))
                                    .invokeinterface(bsonWriterClassDesc, "writeName",
                                            MethodTypeDesc.of(CD_void, CD_String));

                            cob
                                    .aload(encoderContextSlot)
                                    .aload(thisSlot)
                                    .getfield(recordCodecClassDesc, name + "Codec", codecClassDesc)
                                    .aload(writerSlot)
                                    .aload(recordClassSlot)
                                    .invokevirtual(recordClassDesc, name, recordComponentMtd)
                                    .invokevirtual(encoderContextClassDesc, "encodeWithChildContext",
                                            MethodTypeDesc.of(CD_void, encoderClassDesc, bsonWriterClassDesc, CD_Object))
                                    .labelBinding(l0);
                        }

                        cob
                                .aload(writerSlot)
                                .invokeinterface(bsonWriterClassDesc, "writeEndDocument", MethodTypeDesc.of(CD_void))
                                .return_();
                    });

            // generate bridge method
            clb.withMethodBody("encode",
                    MethodTypeDesc.of(CD_void, bsonWriterClassDesc, ConstantDescs.CD_Object, encoderContextClassDesc),
                    ACC_PUBLIC,
                    cob -> cob
                            .aload(0)
                            .aload(1)
                            .aload(2)
                            .checkcast(recordClassDesc)
                            .aload(3)
                            .invokevirtual(recordCodecClassDesc, "encode", methodTypeDesc)
                            .return_()
            );
        }

        private void generateDecodeMethod(ClassBuilder clb) {
            var methodTypeDesc = MethodTypeDesc.of(recordClassDesc, bsonReaderClassDesc, decoderContextClassDesc);
            var numParams = 3;
            var readerSlot = 1;
            var decoderContextSlot = 2;
            var firstComponentValueSlot = 3;
            var lastComponentValueSlot = firstComponentValueSlot + numParams - 1;
            var nameSlot = lastComponentValueSlot + 1;
            clb.withMethodBody("decode",
                    methodTypeDesc,
                    ACC_PUBLIC,
                    cob -> {
                        // create a local variable for each component
                        for (int i = 0; i < recordClass.getRecordComponents().length; i++) {
                            cob
                                    .aconst_null()
                                    .astore(firstComponentValueSlot + i);
                        }

                        var startLoopLabel = cob.newLabel();
                        var endLoopLabel = cob.newLabel();
                        var endElseLabel = cob.newLabel();

                        cob
                                .aload(readerSlot)
                                .invokeinterface(bsonReaderClassDesc, "readStartDocument", MethodTypeDesc.of(CD_void));

                        cob
                                .labelBinding(startLoopLabel)
                                .aload(readerSlot)
                                .invokeinterface(bsonReaderClassDesc, "readBsonType", MethodTypeDesc.of(bsonTypeClassDesc));

                        cob
                                .getstatic(bsonTypeClassDesc, BsonType.END_OF_DOCUMENT.name(), bsonTypeClassDesc)
                                .if_acmpeq(endLoopLabel);

                        cob
                                .aload(readerSlot)
                                .invokeinterface(bsonReaderClassDesc, "readName", MethodTypeDesc.of(CD_String))
                                .astore(nameSlot);

                        Label curBranchLabel;
                        Label nextBranchLabel = null;
                        for (int i = 0; i < recordClass.getRecordComponents().length; i++) {
                            curBranchLabel = nextBranchLabel;
                            nextBranchLabel = cob.newLabel();
                            var recordComponent = recordClass.getRecordComponents()[i];
                            if (curBranchLabel != null) {
                                cob.labelBinding(curBranchLabel);
                            }
                            cob
                                    .aload(nameSlot)
                                    .ldc(clb.constantPool().stringEntry(recordComponent.getName()))
                                    .invokevirtual(CD_String, "equals", MethodTypeDesc.of(CD_boolean, CD_Object));

                            cob
                                    .ifeq(nextBranchLabel)
                                    .aload(decoderContextSlot)
                                    .aload(thisSlot)
                                    .getfield(recordCodecClassDesc, recordComponent.getName() + "Codec", codecClassDesc)
                                    .aload(readerSlot)
                                    .invokevirtual(decoderContextClassDesc, "decodeWithChildContext",
                                            MethodTypeDesc.of(CD_Object, decoderClassDesc, bsonReaderClassDesc))
                                    .checkcast(ClassDesc.of(recordComponent.getType().getName()))
                                    .astore(firstComponentValueSlot + i)
                                    .goto_(endElseLabel);
                        }

                        cob
                                .labelBinding(nextBranchLabel)
                                .aload(readerSlot)
                                .invokeinterface(bsonReaderClassDesc, "skipValue", MethodTypeDesc.of(CD_void))
                                .labelBinding(endElseLabel);

                        cob
                                .goto_(startLoopLabel)
                                .labelBinding(endLoopLabel)
                                .aload(readerSlot)
                                .invokeinterface(bsonReaderClassDesc, "readEndDocument", MethodTypeDesc.of(CD_void));

                        var paramDescriptors = Arrays.stream(recordClass.getRecordComponents())
                                .map(recordComponent -> ClassDesc.of(recordComponent.getType().getName()))
                                .toList();
                        var recordConstructorMtd = MethodTypeDesc.of(CD_void, paramDescriptors);
                        cob
                                .new_(recordClassDesc)
                                .dup();
                        for (int i = 0; i < recordClass.getRecordComponents().length; i++) {
                            cob.aload(firstComponentValueSlot + i);
                        }

                        cob
                                .invokespecial(recordClassDesc, INIT_NAME, recordConstructorMtd)
                                .return_(TypeKind.REFERENCE);
                    }
            );

            // generate bridge method
            clb.withMethodBody("decode",
                    MethodTypeDesc.of(ConstantDescs.CD_Object, bsonReaderClassDesc, decoderContextClassDesc),
                    ACC_PUBLIC,
                    cob -> cob
                            .aload(0)
                            .aload(1)
                            .aload(2)
                            .invokevirtual(recordCodecClassDesc, "decode", methodTypeDesc)
                            .return_(TypeKind.REFERENCE)
            );
        }
    }

    private static class ByteArrayClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
