/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.ais.model;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akiban.ais.model.Join.GroupingUsage;
import com.akiban.ais.model.Join.SourceType;

// AISBuilder can be used to create an AIS. The API is designed to sify the creation of an AIS during a scan
// of a dump. The user need not search the AIS and hold on to AIS objects (UserTable, Column, etc.). Instead,
// only names from the dump need be supplied. 

public class AISBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(AISBuilder.class);
    // API for creating capturing basic schema information

    public AISBuilder() {
        this(new AkibanInformationSchema(), new DefaultNameGenerator());
    }

    public AISBuilder(NameGenerator nameGenerator) {
        this(new AkibanInformationSchema(), nameGenerator);
    }

    public AISBuilder(AkibanInformationSchema ais) {
        this(ais, new DefaultNameGenerator());
    }

    public AISBuilder(AkibanInformationSchema ais, NameGenerator nameGenerator) {
        LOG.trace("creating builder");
        this.ais = ais;
        this.nameGenerator = nameGenerator;
        if (ais != null) {
            for (UserTable table : ais.getUserTables().values()) {
                tableIdGenerator = Math.max(tableIdGenerator, table.getTableId() + 1);
            }
        }
    }

    public NameGenerator getNameGenerator() {
        return nameGenerator;
    }

    public void setTableIdOffset(int offset) {
        this.tableIdGenerator = offset;
    }
    
    public void setIndexIdOffset (int offset) {
        this.indexIdGenerator = offset;
    }

    public void sequence (String schemaName, String sequenceName,
            long start, long increment,
            long minValue, long maxValue, boolean cycle) {
        LOG.info("sequence: {}.{} ", schemaName,sequenceName);
        Sequence identityGenerator = Sequence.create(ais, schemaName, sequenceName, start, increment, minValue, maxValue, cycle);
        identityGenerator.setTreeName(nameGenerator.generateSequenceTreeName(identityGenerator));
    }
    
    public void userTable(String schemaName, String tableName) {
        LOG.info("userTable: " + schemaName + "." + tableName);
        UserTable.create(ais, schemaName, tableName, tableIdGenerator++);
    }

    public void userTableInitialAutoIncrement(String schemaName,
            String tableName, Long initialAutoIncrementValue) {
        LOG.info("userTableInitialAutoIncrement: " + schemaName + "."
                + tableName + " = " + initialAutoIncrementValue);
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "setting initial autoincrement value", "user table",
                concat(schemaName, tableName));
        table.setInitialAutoIncrementValue(initialAutoIncrementValue);
    }

    public void view(String schemaName, String tableName,
                     String definition, Properties definitionProperties,
                     Map<TableName,Collection<String>> tableColumnReferences) {
        LOG.info("view: " + schemaName + "." + tableName);
        View.create(ais, schemaName, tableName, 
                    definition, definitionProperties, tableColumnReferences);
    }

    public void column(String schemaName, String tableName, String columnName,
            Integer position, String typeName, Long typeParameter1,
            Long typeParameter2, Boolean nullable, Boolean autoIncrement,
            String charset, String collation) {
        column(schemaName, tableName, columnName, position, typeName, typeParameter1, typeParameter2, nullable,
               autoIncrement, charset, collation, null);
    }

    public void column(String schemaName, String tableName, String columnName,
                Integer position, String typeName, Long typeParameter1,
                Long typeParameter2, Boolean nullable, Boolean autoIncrement,
                String charset, String collation, String defaultValue) {
        LOG.info("column: " + schemaName + "." + tableName + "." + columnName);
        Columnar table = ais.getColumnar(schemaName, tableName);
        checkFound(table, "creating column", "user table",
                concat(schemaName, tableName));
        Type type = ais.getType(typeName);
        checkFound(type, "creating column", "type", typeName);
        Column column = Column.create(table, columnName, position, type);
        column.setNullable(nullable);
        column.setAutoIncrement(autoIncrement);
        column.setTypeParameter1(typeParameter1);
        column.setTypeParameter2(typeParameter2);
        column.setCharset(charset);
        column.setCollation(collation);
        column.setDefaultValue(defaultValue);
        column.finishCreating();
    }

    public void columnAsIdentity (String schemaName, String tableName, String columnName,
            String sequenceName, Boolean defaultIdentity) {
        LOG.info("column as identity: " + schemaName + "." + tableName + "." + columnName + ": " + sequenceName);
        Column column = ais.getTable(schemaName, tableName).getColumn(columnName);
        column.setDefaultIdentity(defaultIdentity);
        Sequence identityGenerator = ais.getSequence(new TableName (schemaName, sequenceName));
        column.setIdentityGenerator(identityGenerator);
    }

    public void index(String schemaName, String tableName, String indexName,
            Boolean unique, String constraint) {
        LOG.info("index: " + schemaName + "." + tableName + "." + indexName);
        Table table = ais.getTable(schemaName, tableName);
        checkFound(table, "creating index", "table",
                concat(schemaName, tableName));
        Index index = TableIndex.create(ais, table, indexName, indexIdGenerator++, unique, constraint);
        index.setTreeName(nameGenerator.generateIndexTreeName(index));
    }

    /** @deprecated */
    public void groupIndex(String groupName, String indexName, Boolean unique, Index.JoinType joinType)
    {
        groupIndex(findFullGroupName(groupName), indexName, unique, joinType);
    }

    public void groupIndex(TableName groupName, String indexName, Boolean unique, Index.JoinType joinType)
    {
        LOG.info("groupIndex: " + groupName + "." + indexName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "creating group index", "group", groupName);
        setRootIfNeeded(group);
        String constraint = unique ? Index.UNIQUE_KEY_CONSTRAINT : Index.KEY_CONSTRAINT;
        Index index = GroupIndex.create(ais, group, indexName, indexIdGenerator++, unique, constraint, joinType);
        index.setTreeName(nameGenerator.generateIndexTreeName(index));
    }

    public void indexColumn(String schemaName, String tableName,
            String indexName, String columnName, Integer position,
            Boolean ascending, Integer indexedLength) {
        LOG.info("indexColumn: " + schemaName + "." + tableName + "."
                + indexName + ":" + columnName);
        Table table = ais.getTable(schemaName, tableName);
        checkFound(table, "creating index column", "table",
                concat(schemaName, tableName));
        Column column = table.getColumn(columnName);
        checkFound(column, "creating index column", "column",
                concat(schemaName, tableName, columnName));
        Index index = table.getIndex(indexName);
        checkFound(table, "creating index column", "index",
                concat(schemaName, tableName, indexName));
        IndexColumn.create(index, column, position, ascending, indexedLength);
    }

    /** @deprecated **/
    public void groupIndexColumn(String groupName, String indexName, String schemaName, String tableName,
                                 String columnName, Integer position) {
        groupIndexColumn(findFullGroupName(groupName), indexName, schemaName, tableName, columnName, position);
    }

    public void groupIndexColumn(TableName groupName, String indexName, String schemaName, String tableName,
                                 String columnName, Integer position)
    {
        LOG.info("groupIndexColumn: " + groupName + "." + indexName + ":" + columnName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "creating group index column", "group", groupName);
        Index index = group.getIndex(indexName);
        checkFound(index, "creating group index column", "index", concat(groupName.toString(), indexName));
        Table table = ais.getTable(schemaName, tableName);
        if (!table.getGroup().getName().equals(groupName)) {
            throw new IllegalArgumentException("group name mismatch: " + groupName + " != " + table.getGroup());
        }
        checkFound(table, "creating group index column", "table", concat(schemaName, tableName));
        Column column = table.getColumn(columnName);
        checkFound(column, "creating group index column", "column", concat(schemaName, tableName, columnName));
        IndexColumn.create(index, column, position, true, null);
    }

    public void joinTables(String joinName, String parentSchemaName,
            String parentTableName, String childSchemaName,
            String childTableName) {
        LOG.info("joinTables: " + joinName + ": " + childSchemaName + "."
                + childTableName + " -> " + parentSchemaName + "."
                + parentTableName);
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "creating join", "child table",
                concat(childSchemaName, childTableName));
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        if (parent == null) {
            TableName parentName = new TableName(parentSchemaName,
                    parentTableName);
            ForwardTableReference forwardTableReference = new ForwardTableReference(
                    joinName, parentName, child);
            forwardReferences.put(joinName, forwardTableReference);
        } else {
            Join.create(ais, joinName, parent, child);
        }
    }

    public void joinColumns(String joinName, String parentSchemaName,
            String parentTableName, String parentColumnName,
            String childSchemaName, String childTableName,
            String childColumnName)

    {
        LOG.info("joinColumns: " + joinName + ": " + childSchemaName + "."
                + childTableName + "." + childColumnName + " -> "
                + parentSchemaName + "." + parentTableName + "."
                + parentColumnName);
        // Get child info
        UserTable childTable = ais
                .getUserTable(childSchemaName, childTableName);
        checkFound(childTable, "creating join column", "child table",
                concat(childSchemaName, childTableName));
        Column childColumn = childTable.getColumn(childColumnName);
        checkFound(childColumn, "creating join column", "child column",
                concat(childSchemaName, childTableName, childColumnName));
        // Handle parent - could be a forward reference
        UserTable parentTable = ais.getUserTable(parentSchemaName,
                parentTableName);
        if (parentTable == null) {
            // forward reference
            ForwardTableReference forwardTableReference = forwardReferences
                    .get(joinName);
            forwardTableReference.addColumnReference(parentColumnName,
                    childColumn);
        } else {
            // we've seen the child table
            Column parentColumn = parentTable.getColumn(parentColumnName);
            checkFound(parentColumn, "creating join column", "parent column",
                    concat(parentSchemaName, parentTableName, parentColumnName));
            Join join = ais.getJoin(joinName);
            checkFound(
                    join,
                    "creating join column",
                    "join",
                    concat(parentSchemaName, parentTableName, parentColumnName)
                            + "/"
                            + concat(childSchemaName, childTableName,
                                    childColumnName));
            join.addJoinColumn(parentColumn, childColumn);
        }
    }

    public void routine(String schemaName, String routineName,
                        String language, Routine.CallingConvention callingConvention) {
        LOG.info("routine: {}.{} ", schemaName, routineName);
        Routine routine = Routine.create(ais, schemaName, routineName,
                                               language, callingConvention);
    }
    
    public void parameter(String schemaName, String routineName, 
                          String parameterName, Parameter.Direction direction, 
                          String typeName, Long typeParameter1, Long typeParameter2) {
        LOG.info("parameter: {} {}", concat(schemaName, routineName), parameterName);
        Routine routine = ais.getRoutine(schemaName, routineName);
        checkFound(routine, "creating parameter", "routine", 
                   concat(schemaName, routineName));
        Type type = ais.getType(typeName);
        checkFound(type, "creating parameter", "type", typeName);
        Parameter parameter = Parameter.create(routine, parameterName, direction,
                                               type, typeParameter1, typeParameter2);
    }

    public void routineExternalName(String schemaName, String routineName,
                                    String jarSchema, String jarName, 
                                    String className, String methodName) {
        LOG.info("external name: {} {}", concat(schemaName, routineName), concat(jarName, className, methodName));
        Routine routine = ais.getRoutine(schemaName, routineName);
        checkFound(routine, "external name", "routine", 
                   concat(schemaName, routineName));
        SQLJJar sqljJar = null;
        if (jarName != null) {
            sqljJar = ais.getSQLJJar(jarSchema, jarName);
            checkFound(sqljJar, "external name", "SQJ/J jar", 
                       concat(jarSchema, jarName));
        }
        routine.setExternalName(sqljJar, className, methodName);
    }

    public void routineDefinition(String schemaName, String routineName,
                                  String definition) {
        LOG.info("external name: {} {}", concat(schemaName, routineName), definition);
        Routine routine = ais.getRoutine(schemaName, routineName);
        checkFound(routine, "external name", "routine", 
                   concat(schemaName, routineName));
        routine.setDefinition(definition);
    }

    public void routineSQLAllowed(String schemaName, String routineName,
                                  Routine.SQLAllowed sqlAllowed) {
        LOG.info("SQL allowed: {} {}", concat(schemaName, routineName), sqlAllowed);
        Routine routine = ais.getRoutine(schemaName, routineName);
        checkFound(routine, "SQL allowed", "routine", 
                   concat(schemaName, routineName));
        routine.setSQLAllowed(sqlAllowed);
    }

    public void routineDynamicResultSets(String schemaName, String routineName,
                                         int dynamicResultSets) {
        LOG.info("dynamic result sets: {} {}", concat(schemaName, routineName), dynamicResultSets);
        Routine routine = ais.getRoutine(schemaName, routineName);
        checkFound(routine, "dynamic result sets", "routine", 
                   concat(schemaName, routineName));
        routine.setDynamicResultSets(dynamicResultSets);
    }

    public void sqljJar(String schemaName, String jarName, URL url) {
        LOG.info("SQL/J jar: {}.{} ", schemaName, jarName);
        SQLJJar sqljJar = SQLJJar.create(ais, schemaName, jarName, url);
    }

    public void basicSchemaIsComplete() {
        LOG.info("basicSchemaIsComplete");
        for (ForwardTableReference forwardTableReference : forwardReferences.values()) {
            UserTable childTable = forwardTableReference.childTable();
            UserTable parentTable = ais.getUserTable(forwardTableReference
                    .parentTableName().getSchemaName(), forwardTableReference
                    .parentTableName().getTableName());
            
            if (parentTable != null){
                Join join = Join.create(ais, forwardTableReference.joinName(),
                        parentTable, childTable);
                for (ForwardColumnReference forwardColumnReference : forwardTableReference
                        .forwardColumnReferences()) {
                    Column childColumn = forwardColumnReference.childColumn();
                    Column parentColumn = parentTable
                            .getColumn(forwardColumnReference.parentColumnName());
                    checkFound(childColumn, "marking basic schema complete",
                            "parent column",
                            forwardColumnReference.parentColumnName());
                    join.addJoinColumn(parentColumn, childColumn);
                }
            }
        }
        forwardReferences.clear();
    }

    // API for describing groups

    public void createGroup(String groupName, String groupSchemaName) {
        LOG.info("createGroup: {} in {}", groupName, groupSchemaName);
        Group group = Group.create(ais, groupSchemaName, groupName);
        group.setTreeName(nameGenerator.generateGroupTreeName(groupSchemaName, groupName));
    }

    /** @deprecated **/
    public void deleteGroup(String groupName) {
        deleteGroup(findFullGroupName(groupName));
    }

    public void deleteGroup(TableName groupName) {
        LOG.info("deleteGroup: " + groupName);
        Group group = ais.getGroup(groupName);
        checkFound(group, "deleting group", "group", groupName);
        boolean groupEmpty = true;
        for (UserTable userTable : ais.getUserTables().values()) {
            if (userTable.getGroup() == group) {
                groupEmpty = false;
            }
        }
        if (groupEmpty) {
            ais.deleteGroup(group);
        } else {
            throw new GroupNotEmptyException(group);
        }
    }

    /** @deprecated **/
    public void addTableToGroup(String groupName, String schemaName,
            String tableName) {
        addTableToGroup(findFullGroupName(groupName), schemaName, tableName);
    }

    public void addTableToGroup(TableName groupName, String schemaName, String tableName) {
        LOG.info("addTableToGroup: " + groupName + ": " + schemaName + "."
                + tableName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "adding table to group", "group", groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "adding table to group", "table",
                concat(schemaName, tableName));
        checkGroupAddition(group, table.getGroup(),
                concat(schemaName, tableName));
        setTablesGroup(table, group);
    }

    // addJoinToGroup and removeJoinFromGroup identify a join based on parent
    // and child tables. This is OK for
    // removeJoinFromGroup because of the restrictions on group structure. It
    // DOES NOT WORK for addJoinToGroup,
    // because there could be multiple candidate joins between a pair of tables.

    /** @deprecated  **/
    public void addJoinToGroup(String groupName, String joinName, Integer weight) {
        addJoinToGroup(findFullGroupName(groupName), joinName, weight);
    }

    public void addJoinToGroup(TableName groupName, String joinName, Integer weight) {
        LOG.info("addJoinToGroup: " + groupName + ": " + joinName);
        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "adding join to group", "join", joinName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "adding join to group", "group", groupName);
        // parent
        String parentSchemaName = join.getParent().getName().getSchemaName();
        String parentTableName = join.getParent().getName().getTableName();
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        checkFound(parent, "adding join to group", "parent table",
                concat(parentSchemaName, parentTableName));
        checkGroupAddition(group, parent.getGroup(),
                concat(parentSchemaName, parentTableName));
        setTablesGroup(parent, group);
        // child
        String childSchemaName = join.getChild().getName().getSchemaName();
        String childTableName = join.getChild().getName().getTableName();
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "adding join to group", "child table",
                concat(childSchemaName, childTableName));
        checkGroupAddition(group, child.getGroup(),
                concat(childSchemaName, childTableName));
        checkCycle(child, group);
        setTablesGroup(child, group);
        join.setGroup(group);
        join.setWeight(weight);
        assert join.getParent() == parent : join;
        checkGroupAddition(group, join.getGroup(), joinName);
    }

    public void removeTableFromGroup(String groupName, String schemaName,
            String tableName) {
        removeTableFromGroup(findFullGroupName(groupName), schemaName, tableName);
    }

    public void removeTableFromGroup(TableName groupName, String schemaName, String tableName) {
        LOG.info("removeTableFromGroup: " + groupName + ": " + schemaName + "."
                + tableName);
        // This is only valid for a single-table group.
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "removing join from group", "group", groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "removing join from group", "table table",
                concat(schemaName, tableName));
        checkInGroup(group, table, "removing join from group", "table table");
        if (table.getParentJoin() != null || !table.getChildJoins().isEmpty()) {
            throw new GroupStructureException(
                    "Cannot remove table from a group unless "
                            + "it is the only table in the group, group "
                            + group.getName() + ", table " + table.getName());
        }
        setTablesGroup(table, null);
    }

    /** @deprecated **/
    public void removeJoinFromGroup(String groupName, String joinName) {
        removeJoinFromGroup(findFullGroupName(groupName), joinName);
    }

    public void removeJoinFromGroup(TableName groupName, String joinName) {
        LOG.info("removeJoinFromGroup: " + groupName + ": " + joinName);
        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "removing join from group", "join", joinName);
        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "removing join from group", "group", groupName);
        checkInGroup(group, join, "removing join from group", "child table");
        // parent
        String parentSchemaName = join.getParent().getName().getSchemaName();
        String parentTableName = join.getParent().getName().getTableName();
        UserTable parent = ais.getUserTable(parentSchemaName, parentTableName);
        checkFound(parent, "removing join from group", "parent table",
                concat(parentSchemaName, parentTableName));
        checkInGroup(group, parent, "removing join from group", "parent table");
        // child
        String childSchemaName = join.getChild().getName().getSchemaName();
        String childTableName = join.getChild().getName().getTableName();
        UserTable child = ais.getUserTable(childSchemaName, childTableName);
        checkFound(child, "removing join from group", "child table",
                concat(childSchemaName, childTableName));
        checkInGroup(group, child, "removing join from group", "child table");
        // Remove the join from the group
        join.setGroup(null);
        // Remove the parent from the group if it isn't involved in any other
        // joins in this group.
        if (parent.getChildJoins().size() == 0
                && parent.getParentJoin() == null) {
            setTablesGroup(parent, null);
        }
        // Same for the child (except we know that parent is null)
        assert child.getParentJoin() == null;
        if (child.getChildJoins().size() == 0) {
            setTablesGroup(child, null);
        }
    }

    /** @deprecated **/
    public void moveTreeToGroup(String schemaName, String tableName,
            String groupName, String joinName) {
        moveTreeToGroup(schemaName, tableName, findFullGroupName(groupName), joinName);
    }

    public void moveTreeToGroup(String schemaName, String tableName, TableName groupName, String joinName) {
        LOG.info("moveTree: " + schemaName + "." + tableName + " -> "
                + groupName + " via join " + joinName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "moving tree", "table", concat(schemaName, tableName));

        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "moving tree", "group", groupName);

        // join
        Join join = ais.getJoin(joinName);
        checkFound(join, "moving tree", "join", joinName);

        // Remove table's parent join from its current group (if there is a
        // parent)
        Join parentJoin = table.getParentJoin();
        if (parentJoin != null) {
            parentJoin.setGroup(null);

            // set group usage to NEVER on old parent join
            parentJoin.setGroupingUsage(GroupingUsage.NEVER);
        }

        // Move table to group. Get the children first, because moving the table
        // to another group will cause
        // getChildJoins() to return empty.
        List<Join> children = table.getChildJoins();
        setTablesGroup(table, group);

        // Move the join to the group
        join.setGroup(group);

        // set group usage to ALWAYS on new join
        join.getSourceTypes().add(SourceType.USER);
        join.setGroupingUsage(GroupingUsage.ALWAYS);

        moveTree(children, group);
    }

    public void moveTreeToEmptyGroup(String schemaName, String tableName,
            String groupName) {
        moveTreeToEmptyGroup(schemaName, tableName, findFullGroupName(groupName));
    }

    public void moveTreeToEmptyGroup(String schemaName, String tableName, TableName groupName) {
        LOG.info("moveTree: " + schemaName + "." + tableName
                + " -> empty group " + groupName);
        // table
        UserTable table = ais.getUserTable(schemaName, tableName);
        checkFound(table, "moving tree", "table", concat(schemaName, tableName));

        // group
        Group group = ais.getGroup(groupName);
        checkFound(group, "moving tree", "group", groupName);

        // Remove table's parent join from its current group (if there is a
        // parent)
        Join parentJoin = table.getParentJoin();
        if (parentJoin != null) {
            parentJoin.setGroup(null);
        }
        // find all candidate parent joins and set usage to NEVER to indicate
        // table should be ROOT
        for (Join canParentJoin : table.getCandidateParentJoins()) {
            canParentJoin.setGroupingUsage(GroupingUsage.NEVER);
        }

        // Move table to group. Get the children first (see comment in
        // moveTreeToGroup).
        List<Join> children = table.getChildJoins();
        setTablesGroup(table, group);

        moveTree(children, group);
    }

    public void groupingIsComplete() {
        LOG.info("groupingIsComplete");
        // Hook up root tables
        for(Group group : ais.getGroups().values()) {
            setRootIfNeeded(group);
        }
        // Create hidden PKs if needed. Needs group hooked up before it can be called (to generate index id).
        for (UserTable userTable : ais.getUserTables().values()) {
            userTable.endTable(nameGenerator);
            // endTable may have created new index, set its tree name if so
            Index index = userTable.getPrimaryKeyIncludingInternal().getIndex();
            if (index.getTreeName() == null) {
                index.setTreeName(nameGenerator.generateIndexTreeName(index));
            }
        }
    }

    public void clearGroupings() {
        LOG.info("clear groupings");
        ais.getGroups().clear();
        for (UserTable table : ais.getUserTables().values()) {
            setTablesGroup(table, null);
        }
        for (Join join : ais.getJoins().values()) {
            join.setGroup(null);
        }
    }

    // API for getting the created AIS

    public AkibanInformationSchema akibanInformationSchema() {
        LOG.info("getting AIS");
        return ais;
    }

    private UserTable findRoot(Group group) {
        UserTable root = null;
        for(UserTable table : ais.getUserTables().values()) {
            if((table.getGroup() == group) && table.isRoot()) {
                if(root != null) {
                    return null; // Multiple roots
                }
                root = table;
            }
        }
        return root;
    }

    private void setRootIfNeeded(Group group) {
        if(group.getRoot() == null) {
            group.setRootTable(findRoot(group));
        }
    }

    private void moveTree(List<Join> joins, Group group) {
        LOG.debug("moving tree " + joins + " to group " + group);
        for (Join join : joins) {
            List<Join> children = join.getChild().getChildJoins();
            setTablesGroup(join.getChild(), group);
            join.setGroup(group);
            moveTree(children, group);
        }
    }

    private void checkFound(Object object, String action, String needed, TableName name) {
        checkFound(object, action, needed, name.toString());
    }

    private void checkFound(Object object, String action, String needed,
            String name) {
        if (object == null) {
            throw new NoSuchObjectException(action, needed, name);
        }
    }

    private void checkGroupAddition(Group group, Group existingGroup,
            String name) {
        if (existingGroup != null && existingGroup != group) {
            throw new GroupStructureException(group, existingGroup, name);
        }
    }

    private void checkInGroup(Group group, HasGroup object, String action,
            String objectDescription) {
        if (object.getGroup() != group) {
            throw new NotInGroupException(group, object, action,
                    objectDescription);
        }
    }

    private void checkCycle(UserTable table, Group group) {
        if (table.getGroup() == group) {
            String exception = table + " is already in " + group
                    + ". Group must be acyclic";
            throw new GroupStructureException(exception);
        }
    }

    private String concat(String... strings) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (i > 0) {
                buffer.append(".");
            }
            buffer.append(strings[i]);
        }
        return buffer.toString();
    }

    private void setTablesGroup(Table table, Group group) {
        table.setGroup(group);
    }

    public int getTableIdOffset() {
        return tableIdGenerator;
    }
    
    public int getIndexIdOffset() {
        return indexIdGenerator;
    }

    /**
     * Tree names are normally set when adding a table to a group (all tables in a group
     * must have the same tree name). If testing parts of builder that aren't grouped and
     * LIVE_VALIDATIONS are called, this is a simple work around for that.
     */
    public void setGroupTreeNamesForTest() {
        for(Group group : ais.getGroups().values()) {
            if(group.getTreeName() == null) {
                group.setTreeName(group.getName().toString());
            }
        }
    }

    private TableName findFullGroupName(String groupName) {
        Group group = ais.getGroup(groupName);
        checkFound(group, "looking up group without schema", "group", groupName);
        return group.getName();
    }

    // State
    static final class ColumnName {
        private final TableName table;
        private final String columnName;

        public ColumnName(TableName table, String columnName) {
            this.table = table;
            this.columnName = columnName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((table == null) ? 0 : table.hashCode());
            result = prime * result
                    + ((columnName == null) ? 0 : columnName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this)
                return true;
            if (!(object instanceof ColumnName))
                return false;
            ColumnName other = (ColumnName) object;
            if (this.table == null && other.table != null)
                return false;
            if (!this.table.equals(other.table))
                return false;
            return (this.columnName == null) ? other.columnName == null
                    : this.columnName.equals(other.columnName);
        }
    }

    public final static int MAX_COLUMN_NAME_LENGTH = 64;

    private final AkibanInformationSchema ais;
    private Map<String, ForwardTableReference> forwardReferences = // join name
                                                                   // ->
                                                                   // ForwardTableReference
    new LinkedHashMap<String, ForwardTableReference>();
    private NameGenerator nameGenerator;
    // This is temporary. We need unique ids generated here until the
    // server assigns them.
    private int tableIdGenerator = 0;
    private int indexIdGenerator = 1;

    // Inner classes

    private class ForwardTableReference {
        public ForwardTableReference(String joinName,
                TableName parentTableName, UserTable childTable) {
            this.joinName = joinName;
            this.parentTableName = parentTableName;
            this.childTable = childTable;
        }

        public String joinName() {
            return joinName;
        }

        public TableName parentTableName() {
            return parentTableName;
        }

        public UserTable childTable() {
            return childTable;
        }

        public void addColumnReference(String parentColumnName,
                Column childColumn) {
            forwardColumnReferences.add(new ForwardColumnReference(
                    parentColumnName, childColumn));
        }

        public List<ForwardColumnReference> forwardColumnReferences() {
            return forwardColumnReferences;
        }

        private final String joinName;
        private final UserTable childTable;
        private final TableName parentTableName;
        private final List<ForwardColumnReference> forwardColumnReferences = new ArrayList<ForwardColumnReference>();
    }

    private class ForwardColumnReference {
        public ForwardColumnReference(String parentColumnName,
                Column childColumn) {
            this.parentColumnName = parentColumnName;
            this.childColumn = childColumn;
        }

        public String parentColumnName() {
            return parentColumnName;
        }

        public Column childColumn() {
            return childColumn;
        }

        private final String parentColumnName;
        private final Column childColumn;
    }

    public static class NoSuchObjectException extends RuntimeException {
        public NoSuchObjectException(String action, String needed, String name) {
            super("While " + action + ", could not find " + needed + " " + name);
        }
    }

    public static class GroupStructureException extends RuntimeException {
        public GroupStructureException(Group group, Group existingGroup,
                String name) {
            super(name + " already belongs to group " + existingGroup.getName()
                    + " so it cannot be associated with group "
                    + group.getName());
        }

        public GroupStructureException(String message) {
            super(message);
        }
    }

    public static class GroupNotEmptyException extends RuntimeException {
        public GroupNotEmptyException(Group group) {
            super(
                    "Group "
                            + group.getName()
                            + " cannot be deleted because it contains at least one user table.");
        }
    }

    public class NotInGroupException extends RuntimeException {
        public NotInGroupException(Group group, HasGroup object, String action,
                String objectDescription) {
            super("While " + action + ", found " + objectDescription
                    + " not in " + group + ", but in " + object.getGroup()
                    + " instead.");
        }
    }
}
