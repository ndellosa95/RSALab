package edu.temple.rsalab;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.Nullable;


/**
 * Created by nickdellosa on 2/8/17.
 */

public class RSAProvider extends ContentProvider {


    private static class RSADBHelper extends SQLiteOpenHelper {

        private static final String NAME = "RSAPROV.db";
        private static final int VERSION = 1;
        private static final String CREATEPRIVKEY = "create table privkey (id integer primary key, pk text, pubkey_id integer, foreign key (pubkey_id) references pubkey(id))";
        private static final String CREATEPUBKEY = "create table pubkey (id integer primary key, key_title text unique, pk text)";

        private static final String QUERYKP = "select privkey.pk, pubkey.pk, pubkey.key_title from privkey, pubkey where privkey.pubkey_id=pubkey.id";
        private static final String QUERYPK = "select * from pubkey";
        private static final String QUERYPKFOREXPORT = "select pubkey.pk, pubkey.key_title from pubkey, privkey where privkey.pubkey_id=pubkey.id and pubkey.key_title = ?";
        /*
        private static final String INSERTPR = "insert into privkey (pk, pubkey_id) values (?, ?)";
        private static final String INSERTPU = "insert into pubkey (key_title, pk) values (?, ?)";
        */

        RSADBHelper(Context context) {
            super(context, NAME, null, VERSION);
        }

        public Cursor retrieveKeyPairs() {
            return getReadableDatabase().rawQuery(QUERYKP, null);
        }

        public Cursor retrievePublicKeys() {
            return getReadableDatabase().rawQuery(QUERYPK, null);
        }

        public Cursor retieveKeyForExport(String... keyTitle) {
            return getReadableDatabase().rawQuery(QUERYPKFOREXPORT, keyTitle);
        }

        public long insertPublicKey(String key, String title) {
            ContentValues cv = new ContentValues();
            cv.put("key_title", title);
            cv.put("pk", key);
            return getWritableDatabase().insert("pubkey", null, cv);
        }

        public long insertKeyPair(String pubkey, String privkey, String title) {
            long pubkeyID = insertPublicKey(pubkey, title);

            if (pubkeyID >= 0) {
                ContentValues cv = new ContentValues();
                cv.put("pk", privkey);
                cv.put("pubkey_id", pubkeyID);
                return getWritableDatabase().insert("privkey", null, cv);
            }

            return -1;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATEPUBKEY);
            db.execSQL(CREATEPRIVKEY);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI("edu.temple.rsalab.RSAProvider", "your_keys", 1);
        sUriMatcher.addURI("edu.temple.rsalab.RSAProvider", "imported_keys", 2);
        sUriMatcher.addURI("edu.temple.rsalab.RSAProvider", "addkp", 3);
        sUriMatcher.addURI("edu.temple.rsalab.RSAProvider", "addpk", 4);
        sUriMatcher.addURI("edu.temple.rsalab.RSAProvider", "export_key", 5);
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        RSADBHelper helper = new RSADBHelper(this.getContext());
        Cursor retval = null;
        switch(sUriMatcher.match(uri)) {
            case 1:
                retval = helper.retrieveKeyPairs();
                break;
            case 2:
                retval = helper.retrievePublicKeys();
                break;
            case 5:
                String title = uri.getQueryParameter("title");
                retval = helper.retieveKeyForExport(title);
                break;
        }
        return retval;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        RSADBHelper helper = new RSADBHelper(this.getContext());
        Uri retval = null;
        long success;

        switch(sUriMatcher.match(uri)) {
            case 3:
                success = helper.insertKeyPair(values.getAsString("pubkey"), values.getAsString("privkey"), values.getAsString("key_title"));
                if (success != -1) {
                    retval = (new Uri.Builder()).scheme("content").authority("edu.temple.rsalab.RSAProvider").path("your_keys").build();
                }
                break;
            case 4:
                success = helper.insertPublicKey(values.getAsString("pubkey"), values.getAsString("key_title"));
                if (success != -1) {
                    retval = (new Uri.Builder()).scheme("content").authority("edu.temple.rsalab.RSAProvider").path("imported_keys").build();
                }
                break;
        }

        return retval;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
