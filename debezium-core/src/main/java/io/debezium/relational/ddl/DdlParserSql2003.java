/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational.ddl;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.debezium.annotation.NotThreadSafe;
import io.debezium.relational.Column;
import io.debezium.relational.ColumnEditor;
import io.debezium.relational.TableEditor;
import io.debezium.relational.TableId;
import io.debezium.text.ParsingException;
import io.debezium.text.TokenStream;
import io.debezium.text.TokenStream.Marker;

/**
 * A parser for DDL statements.
 * 
 * @author Randall Hauch
 */
@NotThreadSafe
public class DdlParserSql2003 extends DdlParser {

    /**
     * Create a new DDL parser for SQL-2003.
     */
    public DdlParserSql2003() {
        super(";");
    }

    @Override
    protected void initializeDataTypes(DataTypeParser dataTypes) {
        dataTypes.register(Types.CHAR, "CHARACTER[(L)]");
        dataTypes.register(Types.CHAR, "CHAR[(L)]");
        dataTypes.register(Types.VARCHAR, "CHARACTER VARYING [(L)]");
        dataTypes.register(Types.VARCHAR, "CHAR VARYING [(L)]");
        dataTypes.register(Types.VARCHAR, "VARCHAR[(L)]");
        dataTypes.register(Types.CLOB, "CHARACTER LARGE OBJECT [(L)]");
        dataTypes.register(Types.CLOB, "CHAR LARGE OBJECT [(L)]");
        dataTypes.register(Types.CLOB, "CLOB[(L)]");

        dataTypes.register(Types.NCHAR, "NATIONAL CHARACTER[(L)]");
        dataTypes.register(Types.NCHAR, "NCHAR[(L)]");
        dataTypes.register(Types.NVARCHAR, "NATIONAL CHARACTER VARYING [(L)]");
        dataTypes.register(Types.NVARCHAR, "NCHAR VARYING [(L)]");
        dataTypes.register(Types.NVARCHAR, "NVARCHAR[(L)]");
        dataTypes.register(Types.NCLOB, "NATIONAL CHARACTER LARGE OBJECT [(L)]");
        dataTypes.register(Types.NCLOB, "NCHAR LARGE OBJECT [(L)]");
        dataTypes.register(Types.NCLOB, "NCLOB[(L)]");

        dataTypes.register(Types.BLOB, "BINARY LARGE OBJECT [(L)]");
        dataTypes.register(Types.BLOB, "BLOB[(L)]");

        dataTypes.register(Types.NUMERIC, "NUMERIC[(M[,D])]");
        dataTypes.register(Types.DECIMAL, "DECIMAL[(M[,D])]");
        dataTypes.register(Types.DECIMAL, "DEC[(M[,D])]");
        dataTypes.register(Types.SMALLINT, "SMALLINT");
        dataTypes.register(Types.INTEGER, "INTEGER");
        dataTypes.register(Types.INTEGER, "INT");
        dataTypes.register(Types.BIGINT, "BIGINT");

        dataTypes.register(Types.FLOAT, "FLOAT[(M,D)]");
        dataTypes.register(Types.DOUBLE, "REAL[(M,D)]");
        dataTypes.register(Types.DOUBLE, "DOUBLE PRECISION [(M,D)]");

        dataTypes.register(Types.BOOLEAN, "BOOLEAN");

        dataTypes.register(Types.DATE, "DATE");
        dataTypes.register(Types.TIME, "TIME[(L)] [WITHOUT TIME ZONE]");
        dataTypes.register(Types.TIME_WITH_TIMEZONE, "TIME[(L)] [WITH TIME ZONE]");
        dataTypes.register(Types.TIMESTAMP, "TIMESTAMP[(L)] [WITHOUT TIME ZONE]");
        dataTypes.register(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP[(L)] [WITH TIME ZONE]");
    }

    @Override
    protected void initializeKeywords(TokenSet keywords) {
    }

    @Override
    protected void initializeStatementStarts(TokenSet statementStartTokens) {
        statementStartTokens.add("CREATE", "ALTER", "DROP", "INSERT", "SET", "GRANT", "REVOKE");
    }

    @Override
    protected void parseNextStatement(Marker marker) {
        if (tokens.matches(DdlTokenizer.COMMENT)) {
            parseComment(marker);
        } else if (tokens.matches("CREATE")) {
            parseCreate(marker);
        } else if (tokens.matches("ALTER")) {
            parseAlter(marker);
        } else if (tokens.matches("DROP")) {
            parseDrop(marker);
        } else if (tokens.matches("INSERT")) {
            parseInsert(marker);
        } else if (tokens.matches("SET")) {
            parseSet(marker);
        } else if (tokens.matches("GRANT")) {
            parseGrant(marker);
        } else if (tokens.matches("REVOKE")) {
            parseRevoke(marker);
        } else {
            parseUnknownStatement(marker);
        }
    }

    @Override
    protected void parseCreate(Marker marker) {
        tokens.consume("CREATE");
        tokens.canConsume("OR", "REPLACE");
        if (tokens.matches("TABLE") || tokens.matches("GLOBAL", "TEMPORARY", "TABLE") || tokens.matches("LOCAL", "TEMPORARY", "TABLE")) {
            parseCreateTable(marker);
            debugParsed(marker);
        } else if (tokens.matches("VIEW") || tokens.matches("RECURSIVE", "VIEW")) {
            parseCreateView(marker);
            debugParsed(marker);
        } else {
            parseCreateUnknown(marker);
        }
    }

    protected void parseCreateTable(Marker start) {
        tokens.canConsumeAnyOf("GLOBAL", "LOCAL", "TEMPORARY");
        tokens.consume("TABLE");
        TableId tableId = parseQualifiedTableName(start);
        TableEditor table = databaseTables.editOrCreateTable(tableId);

        if (tokens.matches('(')) {
            // Is either a subquery clause preceded by column name list, or table element list...
            Marker tableContentStart = tokens.mark();
            try {
                parseAsSubqueryClause(start, table);
            } catch (ParsingException e) {
                tokens.rewind(tableContentStart);
                parseTableElementList(start, table);
            }
        } else if (tokens.canConsume("OF")) {
            // Read the qualified name ...
            parseSchemaQualifiedName(start);
            if (tokens.canConsume("UNDER")) {
                // parent table name ...
                parseSchemaQualifiedName(start);
            }
            if (tokens.matches('(')) {
                parseTableElementList(start, table);
            }
        } else if (tokens.canConsume("AS")) {
            parseAsSubqueryClause(start, table);
        }

        if (tokens.canConsume("ON", "COMMIT")) {
            tokens.canConsume("PRESERVE");
            tokens.canConsume("DELETE");
            tokens.consume("ROWS");
        }

        // Update the table definition ...
        databaseTables.overwriteTable(table.create());
    }

    protected void parseAsSubqueryClause(Marker start, TableEditor table) {
        if (tokens.canConsume('(')) {
            // clause begins with a list of column names ...
            tokens.consume(); // column name
            while (tokens.canConsume(',')) {
                tokens.consume(); // column name
            }
            tokens.canConsume(')');
        }
        tokens.consume("AS", "(");
        // skip the subquery definition ...
        tokens.consumeThrough(')', '(');
        tokens.consume("WITH");
        tokens.canConsume("NO");
        tokens.consume("DATA");
    }

    protected void parseTableElementList(Marker start, TableEditor table) {
        tokens.consume('(');
        parseTableElement(start, table);
        while (tokens.canConsume(',')) {
            parseTableElement(start, table);
        }
        tokens.consume(')');
    }

    protected List<String> parseColumnNameList(Marker start) {
        List<String> names = new ArrayList<>();
        tokens.consume('(');
        names.add(tokens.consume());
        while (tokens.canConsume(',')) {
            names.add(tokens.consume());
        }
        tokens.consume(')');
        return names;
    }

    protected void parseTableElement(Marker start, TableEditor table) {
        if (tokens.matchesAnyOf("CONSTRAINT", "UNIQUE", "PRIMARY", "FOREIGN", "CHECK")) {
            parseTableConstraintDefinition(start, table);
        } else if (tokens.matches("LIKE")) {
            parseTableLikeClause(start, table);
        } else if (tokens.matches("REF", "IS")) {
            parseSelfReferencingColumnSpec(start, table);
        } else {
            // Obtain the column editor ...
            String columnName = tokens.consume();
            Column existingColumn = table.columnWithName(columnName);
            ColumnEditor column = existingColumn != null ? existingColumn.edit() : Column.editor().name(columnName);
            AtomicBoolean isPrimaryKey = new AtomicBoolean(false);

            if (tokens.matches("WITH", "OPTIONS")) {
                parseColumnOptions(start, columnName, tokens, column);
            } else {
                parseColumnDefinition(start, columnName, tokens, table, column, isPrimaryKey);
            }

            // Update the table ...
            Column newColumnDefn = column.create();
            table.addColumns(newColumnDefn);
            if (isPrimaryKey.get()) {
                table.setPrimaryKeyNames(newColumnDefn.name());
            }
        }
    }

    protected void parseTableConstraintDefinition(Marker start, TableEditor table) {
        if (tokens.canConsume("CONSTRAINT")) {
            parseSchemaQualifiedName(start); // constraint name
        }
        if (tokens.canConsume("UNIQUE", "(", "VALUE", ")")) {
            table.setUniqueValues();
        } else if (tokens.canConsume("UNIQUE") || tokens.canConsume("PRIMARY", "KEY")) {
            List<String> pkColumnNames = parseColumnNameList(start);
            table.setPrimaryKeyNames(pkColumnNames);
        } else if (tokens.canConsume("FOREIGN", "KEY")) {
            parseColumnNameList(start);
            tokens.consume("REFERENCES");
            parseSchemaQualifiedName(start);
            if (tokens.canConsume('(')) {
                parseColumnNameList(start);
            }
            if (tokens.canConsume("MATCH")) {
                tokens.consumeAnyOf("FULL", "PARTIAL", "SIMPLE");
                if (tokens.canConsume("ON")) {
                    parseReferentialTriggeredActions(start);
                }
            }
        } else if (tokens.canConsume("CHECK", "(")) {
            // Consume everything (we don't care what it is) ...
            tokens.consumeThrough(')', '(');
        }
    }

    protected void parseReferentialTriggeredActions(Marker start) {
        tokens.consume("ON");
        if (tokens.canConsume("UPDATE")) {
            parseReferentialAction(start);
            if (tokens.canConsume("ON", "DELETE")) {
                parseReferentialAction(start);
            }
        } else if (tokens.canConsume("DELETE")) {
            parseReferentialAction(start);
            if (tokens.canConsume("ON", "UPDATE")) {
                parseReferentialAction(start);
            }
        }
    }

    protected void parseReferentialAction(Marker start) {
        if (tokens.canConsume("CASCADE")) {
        } else if (tokens.canConsume("SET", "NULL")) {
        } else if (tokens.canConsume("SET", "DEFAULT")) {
        } else if (tokens.canConsume("RESTRICT")) {
        } else {
            tokens.consume("NO", "ACTION");
        }
    }

    protected void parseTableLikeClause(Marker start, TableEditor table) {
        tokens.consume("LIKE");
        consumeRemainingStatement(start);
    }

    protected void parseSelfReferencingColumnSpec(Marker start, TableEditor table) {
        tokens.consume("REF", "IS");
        consumeRemainingStatement(start);
    }

    protected void parseColumnOptions(Marker start, String columnName, TokenStream tokens, ColumnEditor column) {
        tokens.consume("WITH", "OPTIONS");
        consumeRemainingStatement(start);
    }

    protected void parseColumnDefinition(Marker start, String columnName, TokenStream tokens, TableEditor table, ColumnEditor column,
                                         AtomicBoolean isPrimaryKey) {
        // Parse the data type, which must be at this location ...
        List<ParsingException> errors = new ArrayList<>();
        Marker dataTypeStart = tokens.mark();
        DataType dataType = dataTypeParser.parse(tokens, errors::addAll);
        if (dataType == null) {
            String dataTypeName = parseDomainName(start);
            if (dataTypeName != null) dataType = DataType.userDefinedType(dataTypeName);
        }
        if (dataType == null) {
            // No data type was found
            parsingFailed(dataTypeStart.position(), errors, "Unable to read the data type");
            return;
        }
        column.jdbcType(dataType.jdbcType());
        column.typeName(dataType.name());
        if ( dataType.length() > -1 ) column.length((int)dataType.length());
        if ( dataType.scale() > -1 ) column.scale(dataType.scale());

        if (tokens.matches("REFERENCES", "ARE")) {
            parseReferencesScopeCheck(start, columnName, tokens, column);
        }
        if (tokens.matches("DEFAULT")) {
            parseDefaultClause(start, column);
        } else if (tokens.matches("GENERATED")) {
            parseIdentityColumnSpec(start, column);
        }
        while (tokens.matchesAnyOf("NOT", "UNIQUE", "PRIMARY", "CHECK", "REFERENCES", "CONSTRAINT")) {
            parseColumnConstraintDefinition(start, column, isPrimaryKey);
        }
        if (tokens.canConsume("COLLATE")) {
            parseSchemaQualifiedName(start);
        }
    }

    protected void parseColumnConstraintDefinition(Marker start, ColumnEditor column, AtomicBoolean isPrimaryKey) {
        // Handle the optional constraint name ...
        if (tokens.canConsume("CONSTRAINT")) {
            parseSchemaQualifiedName(start);
        }
        // Handle the constraint ...
        if (tokens.canConsume("NOT", "NULL")) {
            column.optional(false);
        } else if (tokens.canConsume("UNIQUE") || tokens.canConsume("PRIMARY", "KEY")) {
            isPrimaryKey.set(true);
        } else if (tokens.canConsume("REFERENCES", "ARE")) {
            tokens.canConsume("NOT");
            tokens.consume("CHECKED");
            if (tokens.matches("ON", "DELETE")) {
                parseReferentialAction(start);
            }
        } else if (tokens.canConsume("CHECK", "(")) {
            // Consume everything (we don't care what it is) ...
            tokens.consumeThrough(')', '(');
        }
        // Handle the constraint characteristics ...
        parseColumnConstraintCharacteristics(start, column);
    }

    protected void parseColumnConstraintCharacteristics(Marker start, ColumnEditor column) {
        if (tokens.canConsume("INITIALLY")) {
            tokens.consumeAnyOf("DEFERRED", "IMMEDIATE");
            if (tokens.canConsume("NOT", "DEFERRABLE")) {
                // do nothing ...
            } else if (tokens.canConsume("DEFERRABLE")) {
                // do nothing ...
            }
        } else if (tokens.canConsume("NOT", "DEFERRABLE") || tokens.canConsume("DEFERRABLE")) {
            if (tokens.canConsume("INITIALLY")) {
                tokens.consumeAnyOf("DEFERRED", "IMMEDIATE");
            }
        }
    }

    protected void parseIdentityColumnSpec(Marker start, ColumnEditor column) {
        column.generated(true);
        column.autoIncremented(true);
        column.optional(false);
        tokens.consume("GENERATED");
        if (tokens.canConsume("BY")) {
            tokens.consume("DEFAULT");
        } else {
            tokens.consume("ALWAYS");
            if (tokens.canConsume("AS", "(")) {
                // Consume everything (we don't care what it is) ...
                tokens.consumeThrough(')', '(');
                return;
            }
        }
        tokens.consume("AS", "IDENTITY");
        if (tokens.canConsume('(')) {
            // Consume everything (we don't care what it is) ...
            tokens.consumeThrough(')', '(');
        }
    }

    protected void parseDefaultClause(Marker start, ColumnEditor column) {
        tokens.consume("DEFAULT");
        if (tokens.canConsume("CURRENT", "DATE")) {
            // do nothing, since we don't really care too much about the default value as a function
        } else if (tokens.canConsume("CURRENT", "TIME") || tokens.canConsume("CURRENT", "TIMESTAMP")
                || tokens.canConsume("LOCALTIME") || tokens.canConsume("LOCALTIMESTAMP")) {
            if (tokens.canConsume('(')) {
                tokens.consumeInteger(); // precision
                tokens.consume(')');
            }
            // do nothing, since we don't really care too much about the default value as a function
        } else if (tokens.canConsume("USER") || tokens.canConsume("CURRENT", "USER") || tokens.canConsume("CURRENT", "ROLE")
                || tokens.canConsume("SESSION", "USER") || tokens.canConsume("SYSTEM", "USER") || tokens.canConsume("CURRENT", "PATH")) {
            // do nothing, since we don't really care too much about the default value as a function
        } else if (tokens.canConsume("NULL")) {
            // do nothing ...
        } else if (tokens.canConsume("ARRAY", "[", "]")) {
            // do nothing ...
        } else if (tokens.canConsume("MULTISET", "[", "]")) {
            // do nothing ...
        } else {
            parseLiteral(start);
            // do nothing ...
        }
    }

    protected String parseDomainName(Marker start) {
        return parseSchemaQualifiedName(start);
    }

    protected Object parseLiteral(Marker start) {
        if (tokens.canConsume('_')) { // introducer
            // This is a character literal beginning with a character set ...
            parseCharacterSetName(start);
            return parseCharacterLiteral(start);
        }
        if (tokens.canConsume('N')) {
            return parseCharacterLiteral(start);
        }
        if (tokens.canConsume("U", "&")) {
            return parseCharacterLiteral(start);
        }
        if (tokens.canConsume('X')) {
            return parseCharacterLiteral(start);
        }
        if (tokens.canConsume("DATE")) {
            return parseDateLiteral(start);
        }
        if (tokens.canConsume("TIME")) {
            return parseDateLiteral(start);
        }
        if (tokens.canConsume("TIMESTAMP")) {
            return parseDateLiteral(start);
        }
        if (tokens.canConsume("INTERVAL")) {
            return parseIntervalLiteral(start);
        }
        if (tokens.canConsume("TRUE")) {
            return Boolean.TRUE;
        }
        if (tokens.canConsume("FALSE")) {
            return Boolean.FALSE;
        }
        if (tokens.canConsume("UNKNOWN")) {
            return Boolean.FALSE;
        }
        // Otherwise, it's just a numeric literal ...
        return parseNumericLiteral(start, true);
    }

    protected Object parseNumericLiteral(Marker start, boolean signed) {
        StringBuilder sb = new StringBuilder();
        boolean decimal = false;
        if (signed && tokens.matches("+", "-")) {
            sb.append(tokens.consumeAnyOf("+", "-"));
        }
        if (!tokens.canConsume('.')) {
            sb.append(tokens.consumeInteger());
        }
        if (tokens.canConsume('.')) {
            sb.append(tokens.consumeInteger());
            decimal = true;
        }
        if (!tokens.canConsume('E')) {
            if (decimal) return Double.parseDouble(sb.toString());
            return Integer.parseInt(sb.toString());
        }
        sb.append('E');
        if (tokens.matches("+", "-")) {
            sb.append(tokens.consumeAnyOf("+", "-"));
        }
        sb.append(tokens.consumeInteger());
        return new BigDecimal(sb.toString());
    }

    protected String parseCharacterLiteral(Marker start) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (tokens.matches(DdlTokenizer.COMMENT)) {
                parseComment(start);
            } else if (tokens.matches(DdlTokenizer.SINGLE_QUOTED_STRING)) {
                if (sb.length() != 0) sb.append(' ');
                sb.append(tokens.consume());
            } else {
                break;
            }
        }
        if (tokens.canConsume("ESCAPE")) {
            tokens.consume();
        }
        return sb.toString();
    }

    protected String parseCharacterSetName(Marker start) {
        String name = tokens.consume();
        if (tokens.canConsume('.')) {
            // The name was actually a schema name ...
            String id = tokens.consume();
            return name + "." + id;
        }
        return name;
    }

    protected String parseDateLiteral(Marker start) {
        return consumeQuotedString();
    }

    protected String parseTimeLiteral(Marker start) {
        return consumeQuotedString();
    }

    protected String parseTimestampLiteral(Marker start) {
        return consumeQuotedString();
    }

    protected String parseIntervalLiteral(Marker start) {
        tokens.consume("INTERVAL");
        boolean negative = false;
        if (tokens.canConsume('+')) {
            negative = false;
        } else if (tokens.canConsume('-')) {
            negative = true;
        }
        String str = parseIntervalString(start);
        String qual = parseIntervalQualifier(start);
        return (negative ? "-" : "+") + str + " " + qual;
    }

    protected String parseIntervalString(Marker start) {
        return tokens.consumeAnyOf(DdlTokenizer.SINGLE_QUOTED_STRING, DdlTokenizer.DOUBLE_QUOTED_STRING);
    }

    protected String parseIntervalQualifier(Marker start) {
        StringBuilder sb = new StringBuilder();
        sb.append(tokens.consumeAnyOf("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND"));
        if (tokens.canConsume('(')) {
            int precision = tokens.consumeInteger();
            sb.append(" (").append(precision);
            if (tokens.canConsume(',')) {
                int scale = tokens.consumeInteger();
                sb.append(",").append(scale);
            }
            tokens.consume(')');
            sb.append(")");
        }
        if (tokens.canConsume("TO")) {
            sb.append(" TO ");
            sb.append(tokens.consumeAnyOf("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND"));
            if (tokens.canConsume('(')) {
                sb.append("(").append(tokens.consumeInteger()).append(")");
                tokens.consume(')');
            }
        }
        return sb.toString();
    }

    protected void parseReferencesScopeCheck(Marker start, String columnName, TokenStream tokens, ColumnEditor column) {
        tokens.consume("REFERENCES", "ARE");
        tokens.canConsume("NOT"); // optional
        tokens.consume("CHECKED");
        if (tokens.canConsume("ON", "DELETE")) {
            if (tokens.canConsume("CASCADE")) {
            } else if (tokens.canConsume("SET", "NULL")) {
            } else if (tokens.canConsume("SET", "DEFAULT")) {
            } else if (tokens.canConsume("RESTRICT")) {
            } else {
                tokens.consume("NO", "ACTION");
            }
        }
    }

    protected void parseCreateView(Marker start) {
        tokens.canConsume("RECURSIVE");
        tokens.consume("VIEW");
        TableId tableId = parseQualifiedTableName(start);
        TableEditor table = databaseTables.editOrCreateTable(tableId);

        if (tokens.canConsume("OF")) {
            // Read the qualified name ...
            parseSchemaQualifiedName(start);
            if (tokens.canConsume("UNDER")) {
                // parent table name ...
                parseSchemaQualifiedName(start);
            }
            if (tokens.matches('(')) {
                parseColumnNameList(start);
            }
        } else if (tokens.matches('(')) {
            parseColumnNameList(start);
        }
        tokens.canConsume("AS");
        // We don't care about the rest ...
        consumeRemainingStatement(start);

        // Update the table definition ...
        databaseTables.overwriteTable(table.create());
    }

    protected void parseCreateUnknown(Marker start) {
        consumeRemainingStatement(start);
    }

    @Override
    protected void parseAlter(Marker marker) {
        tokens.consume("ALTER");
        if (tokens.matches("TABLE") || tokens.matches("IGNORE", "TABLE")) {
            parseAlterTable(marker);
            debugParsed(marker);
        } else {
            parseAlterUnknown(marker);
        }
    }

    protected void parseAlterTable(Marker start) {
        tokens.canConsume("IGNORE");
        tokens.consume("TABLE");
        TableId tableId = parseQualifiedTableName(start);
        TableEditor table = databaseTables.editOrCreateTable(tableId);

        if (tokens.matches("ADD", "CONSTRAINT") || tokens.matches("ADD", "UNIQUE") || tokens.matches("ADD", "PRIMARY")
                || tokens.matches("ADD", "FOREIGN") || tokens.matches("ADD", "CHECK")) {
            tokens.consume("ADD");
            parseTableConstraintDefinition(start, table);
        } else if (tokens.canConsume("ADD", "COLUMN") || tokens.canConsume("ADD")) {
            // Adding a column ...
            String columnName = tokens.consume();
            ColumnEditor column = Column.editor().name(columnName);
            AtomicBoolean isPrimaryKey = new AtomicBoolean(false);
            parseColumnDefinition(start, columnName, tokens, table, column, isPrimaryKey);

            // Update the table ...
            Column newColumnDefn = column.create();
            table.setColumns(newColumnDefn);
            if (isPrimaryKey.get()) {
                table.setPrimaryKeyNames(newColumnDefn.name());
            }
        } else if (tokens.canConsume("ALTER", "COLUMN") || tokens.canConsume("ALTER")) {
            // Altering a column ...
            String columnName = tokens.consume();
            Column existingColumn = table.columnWithName(columnName);
            ColumnEditor column = existingColumn != null ? existingColumn.edit() : Column.editor().name(columnName);
            parseAlterColumn(start, column);
            // Update the table ...
            Column newColumnDefn = column.create();
            table.setColumns(newColumnDefn);
        } else if (tokens.matches("DROP", "CONSTRAINT")) {
            parseDropTableConstraint(start, table);
        } else if (tokens.canConsume("DROP", "COLUMN") || tokens.canConsume("DROP")) {
            parseDropColumn(start, table);
        }

        databaseTables.overwriteTable(table.create());
    }

    protected void parseDropColumn(Marker start, TableEditor table) {
        String columnName = tokens.consume();
        table.removeColumn(columnName);
        tokens.consumeAnyOf("CASCADE", "RESTRICT");
    }

    protected void parseDropTableConstraint(Marker start, TableEditor table) {
        tokens.consume("DROP", "CONSTRAINT");
        tokens.consume(); // name
        tokens.consumeAnyOf("CASCADE", "RESTRICT");
    }

    protected void parseAlterColumn(Marker start, ColumnEditor column) {
        if (tokens.canConsume("SET", "INCREMENT", "BY")) {
            parseNumericLiteral(start, true);
            // do nothing ...
        } else if (tokens.canConsume("SET", "MAXVALUE")) {
            parseNumericLiteral(start, true);
            // do nothing ...
        } else if (tokens.canConsume("SET", "NO", "MAXVALUE")) {
            // do nothing ...
        } else if (tokens.canConsume("SET", "MINVALUE")) {
            parseNumericLiteral(start, true);
            // do nothing ...
        } else if (tokens.canConsume("SET", "NO", "MINVALUE")) {
            // do nothing ...
        } else if (tokens.canConsume("SET", "CYCLE")) {
            // do nothing ...
        } else if (tokens.canConsume("SET", "NO", "CYCLE")) {
            // do nothing ...
        } else if (tokens.canConsume("DROP", "DEFAULT")) {
            // do nothing ...
        } else if (tokens.canConsume("ADD", "SCOPE")) {
            parseSchemaQualifiedName(start);
            // do nothing ...
        } else if (tokens.canConsume("DROP", "SCOPE")) {
            tokens.consumeAnyOf("CASCADE", "RESTRICT");
            // do nothing ...
        } else if (tokens.canConsume("SET")) {
            parseDefaultClause(start, column);
        }
    }

    protected void parseAlterUnknown(Marker start) {
        consumeRemainingStatement(start);
        debugSkipped(start);
    }

    @Override
    protected void parseDrop(Marker marker) {
        tokens.consume("DROP");
        if (tokens.matches("TABLE") || tokens.matches("TEMPORARY", "TABLE")) {
            parseDropTable(marker);
            debugParsed(marker);
        } else if (tokens.matches("VIEW")) {
            parseDropView(marker);
            debugParsed(marker);
        } else {
            parseDropUnknown(marker);
        }
    }

    protected void parseDropTable(Marker start) {
        tokens.canConsume("TEMPORARY");
        tokens.consume("TABLE");
        tokens.canConsume("IF", "EXISTS");
        TableId tableId = parseQualifiedTableName(start);
        databaseTables.removeTable(tableId);
        // ignore the rest ...
        consumeRemainingStatement(start);
    }

    protected void parseDropView(Marker start) {
        tokens.consume("VIEW");
        tokens.canConsume("IF", "EXISTS");
        TableId tableId = parseQualifiedTableName(start);
        databaseTables.removeTable(tableId);
        // ignore the rest ...
        consumeRemainingStatement(start);
    }

    protected void parseDropUnknown(Marker start) {
        consumeRemainingStatement(start);
        debugSkipped(start);
    }

    protected void parseInsert(Marker marker) {
        consumeStatement();
        debugSkipped(marker);
    }

    protected void parseSet(Marker marker) {
        consumeStatement();
        debugSkipped(marker);
    }

    protected void parseGrant(Marker marker) {
        consumeStatement();
        debugSkipped(marker);
    }

    protected void parseRevoke(Marker marker) {
        consumeStatement();
        debugSkipped(marker);
    }
}
