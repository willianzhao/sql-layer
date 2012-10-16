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

package com.akiban.sql.embedded;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.UserTable;

import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class ExecuteAutoGeneratedKeys
{
    public abstract List<Column> getTargetColumns(UserTable targetTable) throws SQLException;

    static ExecuteAutoGeneratedKeys of(int autoGeneratedKeys) {
        switch (autoGeneratedKeys) {
        case Statement.NO_GENERATED_KEYS:
            return null;
        case Statement.RETURN_GENERATED_KEYS:
            return new ExecuteAutoGeneratedKeys() {
                    @Override
                    public List<Column> getTargetColumns(UserTable targetTable) {
                        Column identityColumn = targetTable.getIdentityColumn();
                        if (identityColumn == null)
                            return Collections.emptyList();
                        else
                            return Collections.singletonList(identityColumn);
                    }
                };
        default:
            throw new IllegalArgumentException("Invalid autoGeneratedKeys: " + autoGeneratedKeys);
        }
    }

    static ExecuteAutoGeneratedKeys of(final int[] columnIndexes) {
        return new ExecuteAutoGeneratedKeys() {
                @Override
                public List<Column> getTargetColumns(UserTable targetTable) throws SQLException {
                    List<Column> result = new ArrayList<Column>();
                    for (int i = 0; i < columnIndexes.length; i++) {
                        int columnIndex = columnIndexes[i];
                        if ((columnIndex < 1) || (columnIndex > targetTable.getColumns().size())) {
                            throw new JDBCException("Invalid column index: " + columnIndex);
                        }
                        result.add(targetTable.getColumns().get(columnIndex - 1));
                    }
                    return result;
                }
            };
    }

    static ExecuteAutoGeneratedKeys of(final String[] columnNames) {
        return new ExecuteAutoGeneratedKeys() {
                @Override
                public List<Column> getTargetColumns(UserTable targetTable) throws SQLException {
                    List<Column> result = new ArrayList<Column>();
                    for (int i = 0; i < columnNames.length; i++) {
                        String columnName = columnNames[i];
                        Column column = targetTable.getColumn(columnName);
                        if (column == null) {
                            throw new JDBCException("Invalid column name: " + columnName);
                        }
                        result.add(column);
                    }
                    return result;
                }
            };
    }

}
