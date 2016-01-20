/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.data;

import java.util.BitSet;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;

/**
 * A set of bits of arbitrary length.
 * 
 * @author Randall Hauch
 */
public class Bits {

    public static final String LOGICAL_NAME = "io.debezium.data.Bits";

    /**
     * Returns a {@link SchemaBuilder} for a Bits. You can use the resulting SchemaBuilder
     * to set additional schema settings such as required/optional, default value, and documentation.
     * @return the schema builder
     */
    public static SchemaBuilder builder() {
        return SchemaBuilder.bytes()
                .name(LOGICAL_NAME)
                .version(1);
    }

    /**
     * Returns a Schema for a Bits but with all other default Schema settings.
     * @return the schema
     * @see #builder()
     */
    public static Schema schema() {
        return builder().build();
    }

    /**
     * Convert a value from its logical format (BitSet) to it's encoded format.
     * @param schema the schema
     * @param value the logical value
     * @return the encoded value
     */
    public static byte[] fromLogical(Schema schema, BitSet value) {
        return value.toByteArray();
    }

    public static BitSet toLogical(Schema schema, byte[] value) {
        return BitSet.valueOf(value);
    }
}
