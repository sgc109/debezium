/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational;

import java.util.function.Function;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import io.debezium.annotation.Immutable;

/**
 * Defines the Kafka Connect {@link Schema} functionality associated with a given {@link Table table definition}, and which can
 * be used to send rows of data that match the table definition to Kafka Connect.
 * <p>
 * Given a {@link Table} definition, creating and using a {@link TableSchema} is straightforward:
 * 
 * <pre>
 * Table table = ...
 * TableSchema tableSchema = new TableSchemaBuilder().create(table);
 * </pre>
 * 
 * or use a subclass of {@link TableSchemaBuilder} for the particular DBMS. Then, for each row of data:
 * 
 * <pre>
 * Object[] data = ...
 * Object key = tableSchema.keyFromColumnData(data);
 * Struct value = tableSchema.valueFromColumnData(data);
 * Schema keySchema = tableSchema.keySchema();
 * Schema valueSchema = tableSchema.valueSchema();
 * </pre>
 * 
 * all of which can be handed to Kafka Connect to create a new record.
 * <p>
 * When the table structure changes, simply obtain a new or updated {@link Table} definition (e.g., via an {@link Table#edit()
 * editor}), rebuild the {@link TableSchema} for that {@link Table}, and use the new {@link TableSchema} instance for subsequent
 * records.
 * 
 * @author Randall Hauch
 * @see TableSchemaBuilder
 */
@Immutable
public class TableSchema {

    private final Schema keySchema;
    private final Schema valueSchema;
    private final Function<Object[], Object> keyGenerator;
    private final Function<Object[], Struct> valueGenerator;

    /**
     * Create an instance with the specified {@link Schema}s for the keys and values, and the functions that generate the
     * key and value for a given row of data.
     * 
     * @param keySchema the schema for the primary key; may be null
     * @param keyGenerator the function that converts a row into a single key object for Kafka Connect; may not be null but may
     *            return nulls
     * @param valueSchema the schema for the values; may be null
     * @param valueGenerator the function that converts a row into a single value object for Kafka Connect; may not be null but
     *            may return nulls
     */
    public TableSchema(Schema keySchema, Function<Object[], Object> keyGenerator,
            Schema valueSchema, Function<Object[], Struct> valueGenerator) {
        this.keySchema = keySchema;
        this.valueSchema = valueSchema;
        this.keyGenerator = keyGenerator != null ? keyGenerator : (row) -> null;
        this.valueGenerator = valueGenerator != null ? valueGenerator : (row) -> null;
    }

    /**
     * Get the {@link Schema} that represents the table's columns, excluding those that make up the {@link #keySchema()}.
     * 
     * @return the Schema describing the columns in the table; never null
     */
    public Schema valueSchema() {
        return valueSchema;
    }

    /**
     * Get the {@link Schema} that represents the table's primary key.
     * 
     * @return the Schema describing the column's that make up the primary key; null if there is no primary key
     */
    public Schema keySchema() {
        return keySchema;
    }

    /**
     * Convert the specified row of values into a Kafka Connect key. The row is expected to conform to the structured defined
     * by the table.
     * 
     * @param columnData the column values for the table
     * @return the key, or null if the {@code columnData}
     */
    public Object keyFromColumnData(Object[] columnData) {
        return columnData == null ? null : keyGenerator.apply(columnData);
    }

    /**
     * Convert the specified row of values into a Kafka Connect value. The row is expected to conform to the structured defined
     * by the table.
     * 
     * @param columnData the column values for the table
     * @return the value, or null if the {@code columnData}
     */
    public Struct valueFromColumnData(Object[] columnData) {
        return columnData == null ? null : valueGenerator.apply(columnData);
    }
}
