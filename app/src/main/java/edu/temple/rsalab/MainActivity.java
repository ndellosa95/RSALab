package edu.temple.rsalab;

import android.app.DialogFragment;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Cipher;

public class MainActivity extends AppCompatActivity implements EnterTitleFragment.KeyGenerator {

    private static final String TEXTFLAG = "#txt\t";
    private static final String KEYFLAG = "#key\t";
    private final HashMap<String, String> pubKeys = new HashMap<>();
    private final HashMap<String, String> privKeys = new HashMap<>();
    private final List<String> titles = new LinkedList<>();
    private final List<String> privTitles = new LinkedList<>();
    private final List<String> beamList = new LinkedList<>();
    private NfcAdapter mNfcAdapter;
    private String SHARETEXT;

    private class KeyLoaderTask extends AsyncTask<Boolean, Void, HashMap<String, String>> {

        private boolean privateKeys;

        @Override
        protected HashMap<String, String> doInBackground(Boolean... params) {
            HashMap<String, String> retval = new HashMap<>();
            privateKeys = params[0];
            Log.d("private", "private: " + privateKeys);
            Cursor cursor = getContentResolver().query(new Uri.Builder().scheme("content").authority("edu.temple.rsalab.RSAProvider").path(
                    privateKeys ? "your_keys" : "imported_keys").build(),
                    null, null, null, null);

            try {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Log.d("addkeylog", "adding in " + privateKeys);
                        if (privateKeys) {
                            retval.put(cursor.getString(2), cursor.getString(0));
                        } else {
                            retval.put(cursor.getString(1), cursor.getString(2));
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return retval;
        }

        @Override
        protected void onPostExecute(HashMap<String, String> result) {
            Log.d("title_size", "size: " + titles.size());
            if (privateKeys) {
                for (String title : result.keySet()) {
                    if (!privKeys.containsKey(title)) {
                        Log.d("adding_title", title);
                        privKeys.put(title, result.get(title));
                        privTitles.add(title);
                        beamList.add(title);
                    }
                }
                ((ArrayAdapter)((Spinner) findViewById(R.id.keyselect2)).getAdapter()).notifyDataSetChanged();
            } else {
                for (String title : result.keySet()) {
                    if (!pubKeys.containsKey(title)) {
                        pubKeys.put(title, result.get(title));
                        titles.add(title);
                    }
                }
                ((ArrayAdapter)((Spinner) findViewById(R.id.keyselect)).getAdapter()).notifyDataSetChanged();
            }
        }
    }

    private void updateKeyList(boolean publicOnly) {
        new KeyLoaderTask().execute(false);
        if (!publicOnly) {
            new KeyLoaderTask().execute(true);
        }
    }

    @Override
    public void generateKey(String title) {
        // generate RSA public and private key and make call to content provider
        KeyPair kp;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(1024);
            kp = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            return;
        }
        ContentValues cv = new ContentValues();
        String pub = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
        Log.d("keyatstart", pub);
        cv.put("privkey", Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP));
        cv.put("pubkey", pub);
        cv.put("key_title", title);
        getContentResolver().insert((new Uri.Builder()).scheme("content").authority("edu.temple.rsalab.RSAProvider").path("addkp").build(), cv);
        updateKeyList(false);
    }

    @Override
    public void importKey(String key, String title) {
        ContentValues cv = new ContentValues();
        cv.put("pubkey", key);
        cv.put("key_title", title);
        getContentResolver().insert(new Uri.Builder().scheme("content").authority("edu.temple.rsalab.RSAProvider").path("addpk").build(), cv);
        updateKeyList(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("intent action", getIntent().getAction());
        this.mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        this.SHARETEXT = getResources().getString(R.string.share_text);

        updateKeyList(false);
        final Button nk = (Button) findViewById(R.id.nk);
        nk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment dialog = new EnterTitleFragment();
                Bundle b = new Bundle();
                b.putBoolean("isImport", false);
                dialog.setArguments(b);
                dialog.show(getFragmentManager(), "title");
            }
        });
/*
        final Button ik = (Button) findViewById(R.id.ik);
        ik.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // look for shared public key
                DialogFragment dialog = new EnterTitleFragment();
                Bundle b = new Bundle();
                b.putBoolean("isImport", true);
                dialog.setArguments(b);
                dialog.getView().findViewById(R.id.importer).setVisibility(View.VISIBLE);
                dialog.show(getFragmentManager(), "title_and_import");
            }
        });
*/
        final Spinner keySpinner = (Spinner) findViewById(R.id.keyselect);
        final Button encrypter = (Button) findViewById(R.id.encrypter);
        final EditText encText = (EditText) findViewById(R.id.encrypt_text);;
        encrypter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object selected = keySpinner.getSelectedItem();
                if (selected != null) {
                    try {
                        Cipher cipher = Cipher.getInstance("RSA");
                        KeyFactory kf = KeyFactory.getInstance("RSA");
                        String sk = pubKeys.get(selected);
                        PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(sk, Base64.NO_WRAP)));
                        cipher.init(Cipher.ENCRYPT_MODE, pk);
                        byte[] skbaprint = encText.getText().toString().getBytes();
                        byte[] skba = cipher.doFinal(skbaprint);
                        String encrypted = new String(Base64.encode(skba, Base64.NO_WRAP));
                        encText.setText(encrypted);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Spinner keySpinner2 = (Spinner) findViewById(R.id.keyselect2);
        final Button decrypter = (Button) findViewById(R.id.decrypter);
        final EditText decText = (EditText) findViewById(R.id.decrypt_text);
        decrypter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // decryption
                Object selected = keySpinner2.getSelectedItem();
                if (selected != null) {
                    try {
                        Cipher cipher = Cipher.getInstance("RSA");
                        KeyFactory kf = KeyFactory.getInstance("RSA");
                        String sk = privKeys.get(selected);
                        PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(sk, Base64.NO_WRAP)));
                        cipher.init(Cipher.DECRYPT_MODE, pk);
                        byte[] skbaprint = Base64.decode(decText.getText().toString().getBytes(), Base64.NO_WRAP);
                        byte[] skba = cipher.doFinal(skbaprint);
                        String decrypted = new String(skba);
                        decText.setText(decrypted);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Spinner beamSpinner = (Spinner) findViewById(R.id.beamselect);
        keySpinner.setAdapter(new ArrayAdapter<String>(this.getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, titles));
        keySpinner2.setAdapter(new ArrayAdapter<String>(this.getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, privTitles));
        beamList.add(SHARETEXT);
        beamList.addAll(privTitles);
        beamSpinner.setAdapter(new ArrayAdapter<String>(this.getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, beamList));
        mNfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
            @Override
            public NdefMessage createNdefMessage(NfcEvent event) {
                String selected = (String) beamSpinner.getSelectedItem();
                Log.d("ndf", "sending ndf message " + selected);
                if (selected != null) {
                    return new NdefMessage(
                            new NdefRecord[] {
                                    NdefRecord.createMime("application/edu.temple.rsalab",
                                            selected.equals(SHARETEXT) ? (TEXTFLAG + encText.getText().toString()).getBytes() :
                                                    (selected + "\t" + KEYFLAG + pubKeys.get(selected)).getBytes())
                            }
                    );
                }
                return null;
            }
        }, this);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d("resume", "action: " + getIntent().getAction());
        if (getIntent().getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            Log.d("ndfr", "receiving ndf message");
            NdefRecord rec = (((NdefMessage)((getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)))[0]).getRecords())[0];
            String s = new String(rec.getPayload());
            if (s.startsWith(TEXTFLAG)) {
                final EditText decText = (EditText) findViewById(R.id.decrypt_text);
                decText.setText(s.substring(TEXTFLAG.length()));
            } else if (s.contains(KEYFLAG)) {
                Log.d("s", s);
                int kfi = s.indexOf(KEYFLAG);
                String title = s.substring(0, kfi-1);
                String key = s.substring(kfi + KEYFLAG.length());
                Log.d("keyis", key);
                this.importKey(key, title);
            }
        }
    }

}
