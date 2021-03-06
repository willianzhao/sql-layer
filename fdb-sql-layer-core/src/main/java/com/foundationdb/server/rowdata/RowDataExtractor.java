/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.rowdata;

import com.foundationdb.server.types.value.ValueSource;
import com.foundationdb.server.types.value.ValueSources;

public final class RowDataExtractor {

    public ValueSource getValueSource(FieldDef fieldDef) {
        int f = fieldDef.getFieldIndex();
        RowDataValueSource source = sources[f];
        if (source == null) {
            source = new RowDataValueSource();
            sources[f] = source;
        }
        source.bind(fieldDef, rowData);
        return source;
    }
    
    public Object get(FieldDef fieldDef) {
        int f = fieldDef.getFieldIndex();
        RowDataValueSource source = sources[f];
        if (source == null) {
            source = new RowDataValueSource();
            sources[f] = source;
        }
        source.bind(fieldDef, rowData);
        return ValueSources.toObject(source);
    }

    public RowDataExtractor(RowData rowData, RowDef rowDef)
    {
        this.rowData = rowData;
        assert rowData != null;
        assert rowDef != null;
        assert rowData.getRowDefId() == rowDef.getRowDefId();
        sources = new RowDataValueSource[rowDef.getFieldCount()];
    }

    private final RowData rowData;
    private final RowDataValueSource[] sources;
}
