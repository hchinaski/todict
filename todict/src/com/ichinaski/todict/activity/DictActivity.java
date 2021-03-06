package com.ichinaski.todict.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.ichinaski.todict.R;
import com.ichinaski.todict.dao.Dict;
import com.ichinaski.todict.fragment.EditDictDialogFragment;
import com.ichinaski.todict.fragment.EditDictDialogFragment.IDictionaryHandler;
import com.ichinaski.todict.provider.DataProviderContract;
import com.ichinaski.todict.provider.DataProviderContract.DictColumns;
import com.ichinaski.todict.provider.DataProviderContract.Word;
import com.ichinaski.todict.provider.DataProviderContract.WordColumns;
import com.ichinaski.todict.util.Extra;
import com.ichinaski.todict.util.Prefs;

public class DictActivity extends BaseActivity implements LoaderCallbacks<Cursor>,
        OnNavigationListener, IDictionaryHandler {
    private ListView mListView;
    private WordAdapter mAdapter;
    
    private ArrayAdapter<String> mNavigationAdapter;
    private List<Dict> mDicts;

    private long mDictID;
    private String mDictName;
    
    private Handler mHandler = new Handler();

    private static final int DICT_LOADER = 0;
    private static final int WORD_LOADER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dict_activity);
        
        BitmapDrawable bg = (BitmapDrawable)getResources().getDrawable(R.drawable.binding_dark);
        bg.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
        getSupportActionBar().setBackgroundDrawable(bg);

        mListView = (ListView)findViewById(android.R.id.list);

        mAdapter = new WordAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(mAdapter);

        mDictID = Prefs.getDefaultDict(this);// Default to the cached value
        mDicts = new ArrayList<Dict>();
        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            long wordID = Long.parseLong(intent.getDataString());
            startWordActivity(wordID);
        }
    }

    private void setup() {
        final ActionBar actionBar = getSupportActionBar();
        final Context context = actionBar.getThemedContext();

        mDictID = Prefs.getDefaultDict(this);

        if (mDicts.size() == 0) {
            // No dictionary available. Prompt new dictionary dialog.
            actionBar.setDisplayShowTitleEnabled(false);
            showEditDictFragment(true);
        } else {
            // List navigation mode
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            String[] names = new String[mDicts.size()];
            int currentIndex = 0;
            for (int i = 0; i < mDicts.size(); i++) {
                final Dict dict = mDicts.get(i);
                names[i] = dict.getName();
                // Select current dictionary (if any)
                if (dict.getID() == mDictID) {
                    currentIndex = i;
                }
            }

            mNavigationAdapter = new ArrayAdapter<String>(context,
                    com.actionbarsherlock.R.layout.sherlock_spinner_item, names);
            mNavigationAdapter
                    .setDropDownViewResource(com.actionbarsherlock.R.layout.sherlock_spinner_dropdown_item);
            actionBar.setListNavigationCallbacks(mNavigationAdapter, this);
            actionBar.setSelectedNavigationItem(currentIndex);
        }
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        final Dict dict = mDicts.get(itemPosition);
        mDictID = dict.getID();
        mDictName = dict.getName();
        Prefs.setDefaultDict(this, mDictID);
        getSupportLoaderManager().restartLoader(WORD_LOADER, null, this);
        return true;
    }

    private void init() {
        getSupportLoaderManager().restartLoader(DICT_LOADER, null, this);
    }

    private void showEditDictFragment(final boolean isNew) {
        // This dialog can be requested after onLoadFInished, thus it cannot
        // be shown in that point. This is a workaround to avoid the error.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                DialogFragment fragment = null;
                if (isNew) {
                    fragment = EditDictDialogFragment.instantiate(Prefs.DICT_NONE, "");
                } else {
                    fragment = EditDictDialogFragment.instantiate(mDictID, mDictName);
                }
                fragment.show(getSupportFragmentManager(), EditDictDialogFragment.TAG);
                
            }
        });
    }

    private void showDeleteDictFragment() {
        DialogFragment df = new DeleteDictDialogFragment();
        df.show(getSupportFragmentManager(), DeleteDictDialogFragment.TAG);
    }

    @Override
    public void newDictionary(String name) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(DictColumns.NAME, name);
        Uri uri = resolver.insert(DataProviderContract.Dict.CONTENT_URI, values);
        mDictID = ContentUris.parseId(uri);
        Prefs.setDefaultDict(this, mDictID);
        init();
    }

    @Override
    public void editDictionary(String name) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(DictColumns.NAME, name);
        resolver.update(DataProviderContract.Dict.CONTENT_URI, 
                values, 
                DictColumns._ID + "= ?",
                new String[] {String.valueOf(mDictID)}
        );
    }

    @Override
    public void deleteDictRequest() {
        // Not yet... Ask for confirmation first
        showDeleteDictFragment();
    }

    private void deleteDict() {
        ContentResolver resolver = getContentResolver();
        if (resolver.delete(DataProviderContract.Dict.CONTENT_URI, 
                DictColumns._ID + "= ?",
                new String[] {String.valueOf(mDictID)}) == 1) {
            Prefs.setDefaultDict(this, Prefs.DICT_NONE);
            mDictID = Prefs.DICT_NONE;
            mDictName = "";
            Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.dict_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search:
                onSearchRequested();
                return true;
            case R.id.add_word:
                startWordActivity(WordActivity.ID_NONE);
                return true;
            case R.id.add_dict:
                showEditDictFragment(true);
                return true;
            case R.id.edit_dict:
                showEditDictFragment(false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case DICT_LOADER:
                return new CursorLoader(this, 
                        DataProviderContract.Dict.CONTENT_URI,
                        DictQuery.PROJECTION, 
                        null, null, null);
            case WORD_LOADER:
                String selection = WordColumns.DICT_ID + " = ?"
                /* + " AND " + WordColumns.STAR + " = ?" */;
                String[] selectionArgs = new String[] {
                        String.valueOf(mDictID)/*, String.valueOf(1) */};
                return new CursorLoader(this, 
                        Word.CONTENT_URI, 
                        WordQuery.PROJECTION, 
                        selection,
                        selectionArgs, 
                        WordColumns.WORD);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case DICT_LOADER:
                mDicts.clear();
                while (cursor.moveToNext()) {
                    Dict dict = new Dict(cursor.getLong(DictQuery._ID),
                            cursor.getString(DictQuery.NAME));
                    mDicts.add(dict);
                }
                setup();
                break;
            case WORD_LOADER:
                mAdapter.changeCursor(cursor);
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == WORD_LOADER) {
            mAdapter.changeCursor(null);
        }
    }

    class WordAdapter extends CursorAdapter implements OnItemClickListener {

        public WordAdapter(Context context) {
            super(context, null, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long id = cursor.getLong(WordQuery._ID);
            final int star = cursor.getInt(WordQuery.STAR);

            TextView word = (TextView)view.findViewById(android.R.id.text1);
            TextView translation = (TextView)view.findViewById(android.R.id.text2);
            final ImageView starView = (ImageView)view.findViewById(R.id.starView);

            word.setText(cursor.getString(WordQuery.WORD));
            translation.setText(cursor.getString(WordQuery.TRANSLATION));
            if (cursor.getInt(WordQuery.STAR) == 0) {
                starView.setImageResource(R.drawable.ic_menu_star);
            } else {
                starView.setImageResource(R.drawable.rate_star_big_on_holo_light);
            }
            starView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int starValue = star == 0 ? 1 : 0;// Just swap the value
                    ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(WordColumns.STAR, starValue);
                    resolver.update(DataProviderContract.Word.CONTENT_URI, 
                            values, 
                            WordColumns._ID + "= ?", 
                            new String[] {String.valueOf(id)}
                    );
                }
            });
            view.setTag(cursor.getLong(WordQuery._ID));
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.word_row, null);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final long wordID = (Long)view.getTag();
            startWordActivity(wordID);
        }
    }

    private void startWordActivity(long id) {
        Intent intent = new Intent(this, WordActivity.class);
        Bundle extras = new Bundle();
        extras.putLong(Extra.WORD_ID, id);
        extras.putLong(Extra.DICT_ID, mDictID);
        extras.putString(Extra.DICT_NAME, mDictName);
        intent.putExtras(extras);
        startActivity(intent);
    }

    public static class DeleteDictDialogFragment extends DialogFragment {
        public static final String TAG = DeleteDictDialogFragment.class.getSimpleName();

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.delete_dict)
                    .setMessage(R.string.delete_dict_msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((DictActivity)getActivity()).deleteDict();
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).create();
        }
    }

    interface DictQuery {
        String[] PROJECTION = {
                DataProviderContract.DictColumns._ID, 
                DataProviderContract.DictColumns.NAME
        };

        int _ID = 0;
        int NAME = 1;
    }

    interface WordQuery {
        String[] PROJECTION = {
                DataProviderContract.WordColumns._ID, 
                DataProviderContract.WordColumns.WORD,
                DataProviderContract.WordColumns.TRANSLATION, 
                DataProviderContract.WordColumns.STAR
        };

        int _ID = 0;
        int WORD = 1;
        int TRANSLATION = 2;
        int STAR = 3;
    }

}
