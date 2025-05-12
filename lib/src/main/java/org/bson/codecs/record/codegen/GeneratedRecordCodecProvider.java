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
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonExtraElements;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.lang.annotation.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_List;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static org.bson.assertions.Assertions.assertNotNull;

public class GeneratedRecordCodecProvider implements CodecProvider {
    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        return get(clazz, List.of(), registry);
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, List<Type> typeArguments, CodecRegistry registry) {
        if (!assertNotNull(clazz).isRecord()) {
            return null;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        Codec<T> result = new RecordCodecGenerator(clazz, typeArguments, registry).generateCodec();
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
        private final List<ComponentModel> componentModels;

        public RecordCodecGenerator(Class<T> recordClass, final List<Type> types, CodecRegistry registry) {
            this.recordClass = recordClass;
            this.recordClassDesc = ClassDesc.of(recordClass.getName());
            this.recordCodecClassDesc = ClassDesc.of("org.bson.codecs.record", recordClass.getSimpleName() + "Codec");
            this.registry = registry;
            this.componentModels = getComponentModels(recordClass, types);
        }

        public Codec<T> generateCodec() {
            var bytes = generateClass();

            // for debugging
//            try {
//                Files.write(Path.of("/tmp", recordClass.getSimpleName() + "Codec.class"), bytes);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }

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
            for (var componentModel : componentModels) {
                clb.withField(componentModel.name + "Codec",
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
            int codeRegistrySlot = 1;
            clb.withMethodBody(INIT_NAME,
                    MethodTypeDesc.of(CD_void, codecRegistryClassDesc),
                    ACC_PUBLIC,
                    cob -> {
                        cob
                                .aload(thisSlot)
                                .invokespecial(ConstantDescs.CD_Object,
                                        INIT_NAME, ConstantDescs.MTD_void);

                        for (var componentModel : componentModels) {
                            cob
                                    .aload(thisSlot)
                                    .aload(codeRegistrySlot)
                                    .ldc(ClassDesc.of(componentModel.rawType.getName()));
                            if (componentModel.typeArguments.isEmpty()) {
                                cob
                                        .invokeinterface(codecRegistryClassDesc, "get", MethodTypeDesc.of(codecClassDesc, CD_Class));
                            } else {
                                for (var typeArgument : componentModel.typeArguments) {
                                    // TODO: these won't all be Class instances
                                    Class<?> typeArgumentClass = (Class<?>) typeArgument;
                                    cob.ldc(ClassDesc.of(typeArgumentClass.getName()));
                                }
                                cob.invokestatic(CD_List, "of", MethodTypeDesc.of(CD_List,
                                        Collections.nCopies(componentModel.typeArguments.size(), CD_Object)), true);
                                cob.invokeinterface(codecRegistryClassDesc, "get", MethodTypeDesc.of(codecClassDesc, CD_Class, CD_List));
                            }
                            cob
                                    .putfield(recordCodecClassDesc,
                                            componentModel.name + "Codec",
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
            int componentValueSlot = 4;
            clb.withMethodBody("encode",
                    methodTypeDesc,
                    ACC_PUBLIC,
                    cob -> {
                        cob
                                .aload(writerSlot)
                                .invokeinterface(bsonWriterClassDesc, "writeStartDocument", MethodTypeDesc.of(CD_void));

                        for (var componentModel : componentModels) {
                            var l0 = cob.newLabel();
                            var recordComponentMtd = MethodTypeDesc.of(componentModel.classDesc);

                            cob
                                    .aload(recordClassSlot)
                                    .invokevirtual(recordClassDesc, componentModel.name, recordComponentMtd);

                            if (componentModel.isNullable) {
                                cob.astore(componentValueSlot);
                            } else {
                                // TODO: handle all primitive types
                                cob.istore(componentValueSlot);
                            }
                            // stack: []
                            if (componentModel.isNullable) {
                                cob
                                        .aload(componentValueSlot)
                                        .ifnull(l0);
                            }
                            cob
                                    .aload(writerSlot)
                                    .ldc(clb.constantPool().stringEntry(componentModel.fieldName))
                                    // stack: [writer, field name]
                                    .invokeinterface(bsonWriterClassDesc, "writeName",
                                            MethodTypeDesc.of(CD_void, CD_String));
                            // stack []
                            cob
                                    .aload(encoderContextSlot)
                                    .aload(thisSlot)
                                    // stack: [encoder context, this]
                                    .getfield(recordCodecClassDesc, componentModel.name + "Codec", codecClassDesc)
                                    // stack: [encoder context, code]
                                    .aload(writerSlot);

                            // stack: [encoder context, encoder, writer]
                            if (!componentModel.isNullable) {
                                // TODO: handle other primitive types
                                cob
                                        .iload(componentValueSlot)
                                        .invokestatic(componentModel.wrapperClassDesc, "valueOf",
                                                MethodTypeDesc.of(ConstantDescs.CD_Integer, List.of(componentModel.classDesc)));
                            } else {
                                cob.aload(componentValueSlot);
                            }
                            // stack: [encoder context, encoder, writer, component value reference]
                            cob
                                    .invokevirtual(encoderContextClassDesc, "encodeWithChildContext",
                                            MethodTypeDesc.of(CD_void, encoderClassDesc, bsonWriterClassDesc, CD_Object));
                            // stack: []
                            if (componentModel.isNullable) {
                                cob
                                        .labelBinding(l0);
                            }
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
            var readerSlot = 1;
            var decoderContextSlot = 2;
            var firstComponentValueSlot = 3;
            var lastComponentValueSlot = firstComponentValueSlot + componentModels.size() - 1;
            var nameSlot = lastComponentValueSlot + 1;
            clb.withMethodBody("decode",
                    methodTypeDesc,
                    ACC_PUBLIC,
                    cob -> {
                        // create a local variable for each component
                        for (var componentModel : componentModels) {
                            if (componentModel.isNullable) {
                                cob
                                        .aconst_null()
                                        .astore(firstComponentValueSlot + componentModel.index);
                            } else {
                                // TODO: support other primitives
                                cob
                                        .iconst_0()
                                        .istore(firstComponentValueSlot + componentModel.index);
                            }
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
                        for (var componentModel : componentModels) {
                            curBranchLabel = nextBranchLabel;
                            nextBranchLabel = cob.newLabel();
                            if (curBranchLabel != null) {
                                cob.labelBinding(curBranchLabel);
                            }
                            cob
                                    .aload(nameSlot)
                                    .ldc(clb.constantPool().stringEntry(componentModel.fieldName))
                                    .invokevirtual(CD_String, "equals", MethodTypeDesc.of(CD_boolean, CD_Object));

                            cob
                                    .ifeq(nextBranchLabel)
                                    .aload(decoderContextSlot)
                                    .aload(thisSlot)
                                    .getfield(recordCodecClassDesc, componentModel.name + "Codec", codecClassDesc)
                                    .aload(readerSlot)
                                    .invokevirtual(decoderContextClassDesc, "decodeWithChildContext",
                                            MethodTypeDesc.of(CD_Object, decoderClassDesc, bsonReaderClassDesc))
                                    .checkcast(ClassDesc.of(componentModel.rawType.getName()));
                            if (componentModel.isNullable) {
                                cob.astore(firstComponentValueSlot + componentModel.index);
                            } else {
                                // TODO: support other primitive types
                                cob.invokevirtual(CD_Integer, "intValue", MethodTypeDesc.of(CD_int));
                                cob.istore(firstComponentValueSlot + componentModel.index);
                            }
                            cob.goto_(endElseLabel);
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

                        var paramDescriptors = componentModels.stream()
                                .map(componentModel -> componentModel.classDesc)
                                .toList();
                        var recordConstructorMtd = MethodTypeDesc.of(CD_void, paramDescriptors);
                        cob
                                .new_(recordClassDesc)
                                .dup();
                        for (var componentModel : componentModels) {
                            if (componentModel.isNullable) {
                                cob.aload(firstComponentValueSlot + componentModel.index);
                            } else {
                                // TODO: support other primitive types
                                cob.iload(firstComponentValueSlot + componentModel.index);
                            }
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

        private static <T> List<ComponentModel> getComponentModels(final Class<T> clazz,
                                                                   final List<Type> typeParameters) {
            var recordComponents = clazz.getRecordComponents();
            var componentModels = new ArrayList<ComponentModel>(recordComponents.length);
            for (int i = 0; i < recordComponents.length; i++) {
                componentModels.add(new ComponentModel(typeParameters, recordComponents[i], i));
            }
            return componentModels;
        }

        private static final class ComponentModel {
            private final String name;
            private final int index;
            private final String fieldName;
            private final boolean isNullable;
            private final ClassDesc classDesc;
            private final ClassDesc wrapperClassDesc;
            private final Class<?> rawType;
            private final List<Type> typeArguments;
            private final BsonType bsonRepresentationType;

            private ComponentModel(final List<Type> typeParameters, final RecordComponent component, final int index) {
                validateAnnotations(component, index);
                this.name = component.getName();
                this.index = index;
                this.fieldName = computeFieldName(component);
                this.isNullable = !component.getType().isPrimitive();
                // TODO: support all primitives
                this.classDesc = component.getType().isPrimitive() ?
                        ConstantDescs.CD_int : ClassDesc.of(component.getType().getName());
                this.wrapperClassDesc = component.getType().isPrimitive() ?
                        ConstantDescs.CD_Integer : classDesc;
                this.rawType = toWrapper(resolveComponentType(typeParameters, component));
                this.typeArguments = (component.getGenericType() instanceof ParameterizedType parameterizedType)
                        ? resolveActualTypeArguments(typeParameters, component.getDeclaringRecord(), parameterizedType)
                        : List.of();
                this.bsonRepresentationType = isAnnotationPresentOnField(component, BsonRepresentation.class)
                        ? getAnnotationOnField(component, BsonRepresentation.class).value()
                        : null;
            }

            private static Class<?> toWrapper(final Class<?> clazz) {
                if (clazz == Integer.TYPE) {
                    return Integer.class;
                } else if (clazz == Long.TYPE) {
                    return Long.class;
                } else if (clazz == Boolean.TYPE) {
                    return Boolean.class;
                } else if (clazz == Byte.TYPE) {
                    return Byte.class;
                } else if (clazz == Character.TYPE) {
                    return Character.class;
                } else if (clazz == Float.TYPE) {
                    return Float.class;
                } else if (clazz == Double.TYPE) {
                    return Double.class;
                } else if (clazz == Short.TYPE) {
                    return Short.class;
                } else {
                    return clazz;
                }
            }

            private static Class<?> resolveComponentType(final List<Type> typeParameters, final RecordComponent component) {
                Type resolvedType = resolveType(component.getGenericType(), typeParameters, component.getDeclaringRecord());
                return resolvedType instanceof Class<?> clazz ? clazz : component.getType();
            }

            private static List<Type> resolveActualTypeArguments(final List<Type> typeParameters, final Class<?> recordClass,
                                                                 final ParameterizedType parameterizedType) {
                return Arrays.stream(parameterizedType.getActualTypeArguments())
                        .map(type -> resolveType(type, typeParameters, recordClass))
                        .toList();
            }

            private static Type resolveType(final Type type, final List<Type> typeParameters, final Class<?> recordClass) {
                return type instanceof TypeVariable<?> typeVariable
                        ? typeParameters.get(getIndexOfTypeParameter(typeVariable.getName(), recordClass))
                        : type;
            }

            // Get
            private static int getIndexOfTypeParameter(final String typeParameterName, final Class<?> recordClass) {
                var typeParameters = recordClass.getTypeParameters();
                for (int i = 0; i < typeParameters.length; i++) {
                    if (typeParameters[i].getName().equals(typeParameterName)) {
                        return i;
                    }
                }
                throw new CodecConfigurationException(format("Could not find type parameter on record %s with name %s",
                        recordClass.getName(), typeParameterName));
            }

            private static String computeFieldName(final RecordComponent component) {
                if (isAnnotationPresentOnField(component, BsonId.class)) {
                    return "_id";
                } else if (isAnnotationPresentOnField(component, BsonProperty.class)) {
                    return getAnnotationOnField(component, BsonProperty.class).value();
                }
                return component.getName();
            }

            private static <T extends Annotation> boolean isAnnotationPresentOnField(final RecordComponent component,
                                                                                     final Class<T> annotation) {
                try {
                    return component.getDeclaringRecord().getDeclaredField(component.getName()).isAnnotationPresent(annotation);
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(format("Unexpectedly missing the declared field for record component %s", component), e);
                }
            }

            private static <T extends Annotation> boolean isAnnotationPresentOnCanonicalConstructorParameter(final RecordComponent component,
                                                                                                             final int index, final Class<T> annotation) {
                return getCanonicalConstructor(component.getDeclaringRecord()).getParameters()[index].isAnnotationPresent(annotation);
            }

            private static <T> Constructor<?> getCanonicalConstructor(final Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor(Arrays.stream(clazz.getRecordComponents())
                            .map(RecordComponent::getType)
                            .toArray(Class<?>[]::new));
                } catch (NoSuchMethodException e) {
                    throw new AssertionError(format("Could not find canonical constructor for record %s", clazz.getName()));
                }
            }

            private static <T extends Annotation> T getAnnotationOnField(final RecordComponent component, final Class<T> annotation) {
                try {
                    return component.getDeclaringRecord().getDeclaredField(component.getName()).getAnnotation(annotation);
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(format("Unexpectedly missing the declared field for recordComponent %s", component), e);
                }
            }

            private static void validateAnnotations(final RecordComponent component, final int index) {
                validateAnnotationNotPresentOnType(component.getDeclaringRecord(), BsonDiscriminator.class);
                validateAnnotationNotPresentOnConstructor(component.getDeclaringRecord(), BsonCreator.class);
                validateAnnotationNotPresentOnMethod(component.getDeclaringRecord(), BsonCreator.class);
                validateAnnotationNotPresentOnFieldOrAccessor(component, BsonIgnore.class);
                validateAnnotationNotPresentOnFieldOrAccessor(component, BsonExtraElements.class);
                validateAnnotationOnlyOnField(component, index, BsonId.class);
                validateAnnotationOnlyOnField(component, index, BsonProperty.class);
                validateAnnotationOnlyOnField(component, index, BsonRepresentation.class);
            }

            private static <T extends Annotation> void validateAnnotationNotPresentOnType(final Class<?> clazz,
                                                                                          @SuppressWarnings("SameParameterValue") final Class<T> annotation) {
                if (clazz.isAnnotationPresent(annotation)) {
                    throw new CodecConfigurationException(format("Annotation '%s' not supported on records, but found on '%s'",
                            annotation, clazz.getName()));
                }
            }

            private static <T extends Annotation> void validateAnnotationNotPresentOnConstructor(final Class<?> clazz,
                                                                                                 @SuppressWarnings("SameParameterValue") final Class<T> annotation) {
                for (var constructor : clazz.getConstructors()) {
                    if (constructor.isAnnotationPresent(annotation)) {
                        throw new CodecConfigurationException(
                                format("Annotation '%s' not supported on record constructors, but found on constructor of '%s'",
                                        annotation, clazz.getName()));
                    }
                }
            }

            private static <T extends Annotation> void validateAnnotationNotPresentOnMethod(final Class<?> clazz,
                                                                                            @SuppressWarnings("SameParameterValue") final Class<T> annotation) {
                for (var method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(annotation)) {
                        throw new CodecConfigurationException(
                                format("Annotation '%s' not supported on methods, but found on method '%s' of '%s'",
                                        annotation, method.getName(), clazz.getName()));
                    }
                }
            }

            private static <T extends Annotation> void validateAnnotationNotPresentOnFieldOrAccessor(final RecordComponent component,
                                                                                                     final Class<T> annotation) {
                if (isAnnotationPresentOnField(component, annotation)) {
                    throw new CodecConfigurationException(
                            format("Annotation '%s' is not supported on records, but found on component '%s' of record '%s'",
                                    annotation.getName(), component, component.getDeclaringRecord()));
                }
                if (component.getAccessor().isAnnotationPresent(annotation)) {
                    throw new CodecConfigurationException(
                            format("Annotation '%s' is not supported on records, but found on accessor for component '%s' of record '%s'",
                                    annotation.getName(), component, component.getDeclaringRecord()));
                }
            }

            private static <T extends Annotation> void validateAnnotationOnlyOnField(final RecordComponent component, final int index,
                                                                                     final Class<T> annotation) {
                if (!isAnnotationPresentOnField(component, annotation)) {
                    if (component.getAccessor().isAnnotationPresent(annotation)) {
                        throw new CodecConfigurationException(format("Annotation %s present on accessor but not component '%s' of record '%s'",
                                annotation.getName(), component, component.getDeclaringRecord()));
                    }
                    if (isAnnotationPresentOnCanonicalConstructorParameter(component, index, annotation)) {
                        throw new CodecConfigurationException(
                                format("Annotation %s present on canonical constructor parameter but not component '%s' of record '%s'",
                                        annotation.getName(), component, component.getDeclaringRecord()));
                    }
                }
            }
        }

        private static class ByteArrayClassLoader extends ClassLoader {
            public Class<?> defineClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }

    }

}
