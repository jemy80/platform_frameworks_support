/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.paging;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.paging.PositionalDataSource;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A simple data source implementation that uses Limit & Offset to page the query.
 * <p>
 * This is NOT the most efficient way to do paging on SQLite. It is
 * <a href="http://www.sqlite.org/cvstrac/wiki?p=ScrollingCursor">recommended</a> to use an indexed
 * ORDER BY statement but that requires a more complex API. This solution is technically equal to
 * receiving a {@link Cursor} from a large query but avoids the need to manually manage it, and
 * never returns inconsistent data if it is invalidated.
 *
 * @param <T> Data type returned by the data source.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public abstract class LimitOffsetDataSource<T> extends PositionalDataSource<T> {
    private final RoomSQLiteQuery mSourceQuery;
    private final String mCountQuery;
    private final String mLimitOffsetQuery;
    private final RoomDatabase mDb;
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationTracker.Observer mObserver;
    private final boolean mInTransaction;

    protected LimitOffsetDataSource(RoomDatabase db, SupportSQLiteQuery query,
            boolean inTransaction, String... tables) {
        this(db, RoomSQLiteQuery.copyFrom(query), inTransaction, tables);
    }

    protected LimitOffsetDataSource(RoomDatabase db, RoomSQLiteQuery query,
            boolean inTransaction, String... tables) {
        mDb = db;
        mSourceQuery = query;
        mInTransaction = inTransaction;
        mCountQuery = "SELECT COUNT(*) FROM ( " + mSourceQuery.getSql() + " )";
        mLimitOffsetQuery = "SELECT * FROM ( " + mSourceQuery.getSql() + " ) LIMIT ? OFFSET ?";
        mObserver = new InvalidationTracker.Observer(tables) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                invalidate();
            }
        };
        db.getInvalidationTracker().addWeakObserver(mObserver);
    }

    /**
     * Count number of rows query can return
     *
     * @hide
     */
    @SuppressWarnings("WeakerAccess")
    public int countItems() {
        final RoomSQLiteQuery sqLiteQuery = RoomSQLiteQuery.acquire(mCountQuery,
                mSourceQuery.getArgCount());
        sqLiteQuery.copyArgumentsFrom(mSourceQuery);
        Cursor cursor = mDb.query(sqLiteQuery);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            cursor.close();
            sqLiteQuery.release();
        }
    }

    @Override
    public boolean isInvalid() {
        mDb.getInvalidationTracker().refreshVersionsSync();
        return super.isInvalid();
    }

    @SuppressWarnings("WeakerAccess")
    protected abstract List<T> convertRows(Cursor cursor);

    @Override
    public void loadInitial(@NonNull LoadInitialParams params,
            @NonNull LoadInitialCallback<T> callback) {
        List<T> list = Collections.emptyList();
        int totalCount = 0;
        int firstLoadPosition = 0;
        RoomSQLiteQuery sqLiteQuery = null;
        Cursor cursor = null;

        mDb.beginTransaction();
        try {
            totalCount = countItems();
            if (totalCount != 0) {
                // bound the size requested, based on known count
                firstLoadPosition = computeInitialLoadPosition(params, totalCount);
                int firstLoadSize = computeInitialLoadSize(params, firstLoadPosition, totalCount);

                sqLiteQuery = getSQLiteQuery(firstLoadPosition, firstLoadSize);
                cursor = mDb.query(sqLiteQuery);
                List<T> rows = convertRows(cursor);
                mDb.setTransactionSuccessful();
                list = rows;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            mDb.endTransaction();
            if (sqLiteQuery != null) {
                sqLiteQuery.release();
            }
        }

        callback.onResult(list, firstLoadPosition, totalCount);
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params,
            @NonNull LoadRangeCallback<T> callback) {
        callback.onResult(loadRange(params.startPosition, params.loadSize));
    }

    /**
     * Return the rows from startPos to startPos + loadCount
     *
     * @hide
     */
    @NonNull
    public List<T> loadRange(int startPosition, int loadCount) {
        final RoomSQLiteQuery sqLiteQuery = getSQLiteQuery(startPosition, loadCount);
        if (mInTransaction) {
            //noinspection deprecation
            mDb.beginTransaction();
            Cursor cursor = null;
            //noinspection TryFinallyCanBeTryWithResources
            try {
                cursor = mDb.query(sqLiteQuery);
                List<T> rows = convertRows(cursor);
                //noinspection deprecation
                mDb.setTransactionSuccessful();
                return rows;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                //noinspection deprecation
                mDb.endTransaction();
                sqLiteQuery.release();
            }
        } else {
            Cursor cursor = mDb.query(sqLiteQuery);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                return convertRows(cursor);
            } finally {
                cursor.close();
                sqLiteQuery.release();
            }
        }
    }

    private RoomSQLiteQuery getSQLiteQuery(int startPosition, int loadCount) {
        final RoomSQLiteQuery sqLiteQuery = RoomSQLiteQuery.acquire(mLimitOffsetQuery,
                mSourceQuery.getArgCount() + 2);
        sqLiteQuery.copyArgumentsFrom(mSourceQuery);
        sqLiteQuery.bindLong(sqLiteQuery.getArgCount() - 1, loadCount);
        sqLiteQuery.bindLong(sqLiteQuery.getArgCount(), startPosition);
        return sqLiteQuery;
    }
}
