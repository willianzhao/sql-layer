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

package com.akiban.qp.persistitadapter.sort;

import com.akiban.server.PersistitKeyValueSource;
import com.akiban.server.PersistitKeyValueTarget;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.std.Comparison;
import com.akiban.server.expression.std.Expressions;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.conversion.Converters;
import com.persistit.Key;
import com.persistit.exception.PersistitException;

import static com.akiban.qp.persistitadapter.sort.SortCursor.SORT_TRAVERSE;

class MixedOrderScanStateSingleSegment extends MixedOrderScanState
{
    @Override
    public boolean startScan() throws PersistitException
    {
        return loSource == null && hiSource == null ? startUnboundedScan() : startBoundedScan();
    }

    @Override
    public boolean advance() throws PersistitException
    {
        return super.advance() && !pastEnd();
    }

    public void setRange(ValueSource lo, ValueSource hi)
    {
        boolean loNull = lo.isNull();
        boolean hiNull = hi.isNull();
        assert !(loNull && hiNull);
        boolean bothNonNull = !loNull && !hiNull;
        loSource = lo;
        hiSource = hi;
        fieldType = loNull ? hiSource.getConversionType() : loSource.getConversionType();
        loEQHi =
            bothNonNull
            ? Expressions.compare(Expressions.valueSource(loSource),
                                  Comparison.EQ,
                                  Expressions.valueSource(hiSource))
            : null;
        endComparison = null;
    }

    public void setRangeLimits(boolean loInclusive, boolean hiInclusive, boolean inequalityOK)
    {
        this.loInclusive = loInclusive;
        this.hiInclusive = hiInclusive;
        if (!inequalityOK && loEQHi != null && !loEQHi.evaluation().eval().getBool()) {
            throw new IllegalArgumentException();
        }
    }

    public MixedOrderScanStateSingleSegment(SortCursorMixedOrder cursor, int field, boolean ascending)
        throws PersistitException
    {
        super(cursor, field, ascending);
        this.keyTarget = new PersistitKeyValueTarget();
        this.keyTarget.attach(cursor.exchange.getKey());
        this.keySource = new PersistitKeyValueSource();
    }

    public MixedOrderScanStateSingleSegment(SortCursorMixedOrder cursor, int field)
        throws PersistitException
    {
        this(cursor, field, cursor.ordering().ascending(field));
    }

    private void setupEndComparison(Comparison comparison, ValueSource bound)
    {
        if (endComparison == null) {
            keySource.attach(cursor.exchange.getKey(), -1, fieldType); // depth unimportant, will be set later
            endComparison =
                Expressions.compare(Expressions.valueSource(keySource),
                                    comparison,
                                    Expressions.valueSource(bound));
        }
    }

    private boolean startUnboundedScan() throws PersistitException
    {
        boolean more;
        Key.Direction direction;
        if (ascending) {
            cursor.exchange.append(Key.BEFORE);
            direction = Key.GT;
        } else {
            cursor.exchange.append(Key.AFTER);
            direction = Key.LT;
        }
        SORT_TRAVERSE.hit();
        more = cursor.exchange.traverse(direction, false);
        return more;
    }

    private boolean startBoundedScan() throws PersistitException
    {
        boolean more;
        // About null handling: See comment in SortCursorUnidirectional.evaluateBoundaries.
        Key.Direction direction;
        if (ascending) {
            if (loSource.isNull()) {
                cursor.exchange.append(null);
                direction = Key.GT;
            } else {
                keyTarget.expectingType(loSource.getConversionType());
                Converters.convert(loSource, keyTarget);
                direction = loInclusive ? Key.GTEQ : Key.GT;
            }
            if (!hiSource.isNull()) {
                setupEndComparison(hiInclusive ? Comparison.LE : Comparison.LT, hiSource);
            }
            // else: endComparison stays null, which causes pastEnd() to always return false.
        } else {
            if (hiSource.isNull()) {
                if (loSource.isNull()) {
                    cursor.exchange.append(null);
                } else {
                    cursor.exchange.append(Key.AFTER);
                }
                direction = Key.LT;
            } else {
                keyTarget.expectingType(hiSource.getConversionType());
                Converters.convert(hiSource, keyTarget);
                direction = hiInclusive ? Key.LTEQ : Key.LT;
            }
            if (!loSource.isNull()) {
                setupEndComparison(loInclusive ? Comparison.GE : Comparison.GT, loSource);
            }
        }
        SORT_TRAVERSE.hit();
        more = cursor.exchange.traverse(direction, false) && !pastEnd();
        return more;
    }

    private boolean pastEnd()
    {
        boolean pastEnd;
        if (endComparison == null) {
            pastEnd = false;
        } else {
            // hiComparisonExpression depends on exchange's key, but we need to compare the correct key segment.
            Key key = cursor.exchange.getKey();
            int keySize = key.getEncodedSize();
            keySource.attach(key, field, fieldType);
            if (keySource.isNull()) {
                pastEnd = !ascending;
            } else {
                ExpressionEvaluation evaluation = endComparison.evaluation();
                pastEnd = !evaluation.eval().getBool();
                key.setEncodedSize(keySize);
            }
        }
        return pastEnd;
    }

    private final PersistitKeyValueTarget keyTarget;
    private final PersistitKeyValueSource keySource;
    private ValueSource loSource;
    private ValueSource hiSource;
    private boolean loInclusive;
    private boolean hiInclusive;
    private Expression endComparison;
    private Expression loEQHi;
    private AkType fieldType;
}
