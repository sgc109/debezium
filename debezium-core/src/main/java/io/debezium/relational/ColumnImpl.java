/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational;

final class ColumnImpl implements Column, Comparable<Column> {
    private final String name;
    private final int position;
    private final int jdbcType;
    private final String typeName;
    private final int length;
    private final int scale;
    private final boolean optional;
    private final boolean autoIncremented;
    private final boolean generated;

    protected ColumnImpl(String columnName, int position, int jdbcType, String typeName, int columnLength, int columnScale,
            boolean optional, boolean autoIncremented, boolean generated) {
        this.name = columnName;
        this.position = position;
        this.jdbcType = jdbcType;
        this.typeName = typeName;
        this.length = columnLength;
        this.scale = columnScale;
        this.optional = optional;
        this.autoIncremented = autoIncremented;
        this.generated = generated;
        assert this.scale >= -1;
        assert this.length >= -1;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int position() {
        return position;
    }

    @Override
    public int jdbcType() {
        return jdbcType;
    }

    @Override
    public String typeName() {
        return typeName;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int scale() {
        return scale;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean isAutoIncremented() {
        return autoIncremented;
    }

    @Override
    public boolean isGenerated() {
        return generated;
    }
    
    @Override
    public boolean equals(Object obj) {
        if ( obj == this ) return true;
        if ( obj instanceof Column ) {
            Column that = (Column)obj;
            return this.name().equalsIgnoreCase(that.name()) &&
                    this.typeName().equalsIgnoreCase(that.typeName()) &&
                    this.jdbcType() == that.jdbcType() &&
                    this.position() == that.position() &&
                    this.length() == that.length() &&
                    this.scale() == that.scale() &&
                    this.isOptional() == that.isOptional() &&
                    this.isAutoIncremented() == that.isAutoIncremented() &&
                    this.isGenerated() == that.isGenerated();
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        sb.append(" ").append(typeName);
        if ( length >= 0 ) {
            sb.append('(').append(length);
            if ( scale >= 0 ) {
                sb.append(',').append(scale);
            }
            sb.append(')');
        }
        if ( optional ) sb.append(" optional");
        if ( autoIncremented ) sb.append(" autoIncr");
        if ( generated ) sb.append(" generated");
        return sb.toString();
    }

}