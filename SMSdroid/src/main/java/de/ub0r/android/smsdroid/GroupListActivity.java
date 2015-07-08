package de.ub0r.android.smsdroid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import java.util.Calendar;

import de.ub0r.android.lib.Utils;
import de.ub0r.android.lib.apis.Contact;
import de.ub0r.android.lib.apis.ContactsWrapper;
import de.ub0r.android.logg0r.Log;

/**
 * Main {@link SherlockActivity} showing conversations.
 *
 * @author flx
 */
public final class GroupListActivity extends SherlockActivity implements
        OnItemClickListener, OnItemLongClickListener {

    /**
     * Tag for output.
     */
    public static final String TAG = "main";

    /**
     * ORIG_URI to resolve.
     */
    static final Uri URI = Uri.parse("content://mms-sms/conversations/");

    /**
     * Number of items.
     */
    private static final int WHICH_N = 6;

    /**
     * Index in dialog: answer.
     */
    private static final int WHICH_ANSWER = 0;

    /**
     * Index in dialog: answer.
     */
    private static final int WHICH_CALL = 1;

    /**
     * Index in dialog: view/add contact.
     */
    private static final int WHICH_VIEW_CONTACT = 2;

    /**
     * Index in dialog: view.
     */
    private static final int WHICH_VIEW = 3;

    /**
     * Index in dialog: delete.
     */
    private static final int WHICH_DELETE = 4;

    /**
     * Index in dialog: mark as spam.
     */
    private static final int WHICH_MARK_SPAM = 5;

    /**
     * Minimum date.
     */
    public static final long MIN_DATE = 10000000000L;

    /**
     * Miliseconds per seconds.
     */
    public static final long MILLIS = 1000L;

    /**
     * Show contact's photo.
     */
    public static boolean showContactPhoto = false;

    /**
     * Show emoticons in {@link MessageListActivity}.
     */
    public static boolean showEmoticons = false;

    /**
     * Dialog items shown if an item was long clicked.
     */
    private String[] longItemClickDialog = null;

    /**
     * Conversations.
     */
    private ConversationAdapter adapter = null;

    /**
     * {@link Calendar} holding today 00:00.
     */
    private static final Calendar CAL_DAYAGO = Calendar.getInstance();

    static {
        // Get time for now - 24 hours
        CAL_DAYAGO.add(Calendar.DAY_OF_MONTH, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        super.onStart();
        AsyncHelper.setAdapter(adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        super.onStop();
        AsyncHelper.setAdapter(null);
    }

    /**
     * Get {@link AbsListView}.
     *
     * @return {@link AbsListView}
     */
    private AbsListView getListView() {
        return (AbsListView) findViewById(android.R.id.list);
    }

    /**
     * Set {@link ListAdapter} to {@link ListView}.
     *
     * @param la ListAdapter
     */
    private void setListAdapter(final ListAdapter la) {
        AbsListView v = getListView();
        if (v instanceof GridView) {
            //noinspection RedundantCast
            ((GridView) v).setAdapter(la);
        } else if (v instanceof ListView) {
            //noinspection RedundantCast
            ((ListView) v).setAdapter(la);
        }

    }

    /**
     * Show all rows of a particular {@link Uri}.
     *
     * @param context {@link Context}
     * @param u       {@link Uri}
     */
    @SuppressWarnings("UnusedDeclaration")
    static void showRows(final Context context, final Uri u) {
        Log.d(TAG, "-----GET HEADERS-----");
        Log.d(TAG, "-- ", u.toString(), " --");
        Cursor c = context.getContentResolver().query(u, null, null, null, null);
        if (c != null) {
            int l = c.getColumnCount();
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < l; i++) {
                buf.append(i).append(":");
                buf.append(c.getColumnName(i));
                buf.append(" | ");
            }
            Log.d(TAG, buf.toString());
        }

    }

    /**
     * Show rows for debugging purposes.
     *
     * @param context {@link Context}
     */
    static void showRows(@SuppressWarnings("UnusedParameters") final Context context) {
        // showRows(ContactsWrapper.getInstance().getUriFilter());
        // showRows(context, URI);
        // showRows(context, Uri.parse("content://sms/"));
        // showRows(context, Uri.parse("content://mms/"));
        // showRows(context, Uri.parse("content://mms/part/"));
        // showRows(context, ConversationProvider.CONTENT_URI);
        // showRows(context, Uri.parse("content://mms-sms/threads"));
        // showRows(Uri.parse(MessageList.URI));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNewIntent(final Intent intent) {
        if (intent != null) {
            Log.d(TAG, "got intent: ", intent.getAction());
            Log.d(TAG, "got uri: ", intent.getData());
            final Bundle b = intent.getExtras();
            if (b != null) {
                Log.d(TAG, "user_query: ", b.get("user_query"));
                Log.d(TAG, "got extra: ", b);
            }
            final String query = intent.getStringExtra("user_query");
            Log.d(TAG, "user query: ", query);
            // TODO: do something with search query
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        final Intent i = getIntent();
        Log.d(TAG, "got intent: ", i.getAction());
        Log.d(TAG, "got uri: ", i.getData());
        Log.d(TAG, "got extra: ", i.getExtras());

        setTheme(PreferencesActivity.getTheme(this));
        Utils.setLocale(this);
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("use_gridlayout", false)) {
            setContentView(R.layout.conversationgrid);
        } else {
            setContentView(R.layout.grouplist);
        }

        // debug info
        showRows(this);

        final AbsListView list = getListView();
        adapter = new ConversationAdapter(this);
        setListAdapter(adapter);
        list.setOnItemClickListener(this);
        list.setOnItemLongClickListener(this);
        longItemClickDialog = new String[WHICH_N];
        longItemClickDialog[WHICH_ANSWER] = getString(R.string.reply);
        longItemClickDialog[WHICH_CALL] = getString(R.string.call);
        longItemClickDialog[WHICH_VIEW_CONTACT] = getString(R.string.view_contact_);
        longItemClickDialog[WHICH_VIEW] = getString(R.string.view_thread_);
        longItemClickDialog[WHICH_DELETE] = getString(R.string.delete_thread_);
        longItemClickDialog[WHICH_MARK_SPAM] = getString(R.string.filter_spam_);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                // check if this is the default sms app.
                // If the device doesn't support Telephony.Sms (i.e. tablet) getDefaultSmsPackage() will
                // be null. Don't show message in this case.
                final String smsPackage = Telephony.Sms.getDefaultSmsPackage(this);
                if (smsPackage != null && !smsPackage.equals(BuildConfig.APPLICATION_ID)) {
                    AlertDialog.Builder b = new AlertDialog.Builder(this);
                    b.setTitle(R.string.not_default_app);
                    b.setMessage(R.string.not_default_app_message);
                    b.setNegativeButton(android.R.string.cancel, null);
                    b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.KITKAT)
                        @Override
                        public void onClick(final DialogInterface dialogInterface, final int i) {
                            Intent intent =
                                    new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                                    BuildConfig.APPLICATION_ID);
                            startActivity(intent);
                        }
                    });
                    b.show();
                }
            } catch (SecurityException e) {
                // some samsung devices/tablets want permission GET_TASKS o.O
                Log.e(TAG, "failed to query default SMS app", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CAL_DAYAGO.setTimeInMillis(System.currentTimeMillis());
        CAL_DAYAGO.add(Calendar.DAY_OF_MONTH, -1);

        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        showContactPhoto = p.getBoolean(PreferencesActivity.PREFS_CONTACT_PHOTO, true);
        showEmoticons = p.getBoolean(PreferencesActivity.PREFS_EMOTICONS, false);
        adapter.startMsgListQuery();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getSupportMenuInflater().inflate(R.menu.conversationlist, menu);
        return true;
    }

    /**
     * Mark all messages with a given {@link Uri} as read.
     *
     * @param context {@link Context}
     * @param uri     {@link Uri}
     * @param read    read status
     */
    static void markRead(final Context context, final Uri uri, final int read) {
        Log.d(TAG, "markRead(", uri, ",", read, ")");
        if (uri == null) {
            return;
        }
        String[] sel = Message.SELECTION_UNREAD;
        if (read == 0) {
            sel = Message.SELECTION_READ;
        }
        final ContentResolver cr = context.getContentResolver();
        final ContentValues cv = new ContentValues();
        cv.put(Message.PROJECTION[Message.INDEX_READ], read);
        try {
            cr.update(uri, cv, Message.SELECTION_READ_UNREAD, sel);
        } catch (IllegalArgumentException | SQLiteException e) {
            Log.e(TAG, "failed update", e);
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        SmsReceiver.updateNewMessageNotification(context, null);
    }

    /**
     * Delete messages with a given {@link Uri}.
     *
     * @param context  {@link Context}
     * @param uri      {@link Uri}
     * @param title    title of Dialog
     * @param message  message of the Dialog
     * @param activity {@link Activity} to finish when deleting.
     */
    static void deleteMessages(final Context context, final Uri uri, final int title,
                               final int message, final Activity activity) {
        Log.i(TAG, "deleteMessages(..,", uri, " ,..)");
        final Builder builder = new Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNegativeButton(android.R.string.no, null);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                try {
                    final int ret = context.getContentResolver().delete(uri, null, null);
                    Log.d(TAG, "deleted: ", ret);
                    if (activity != null && !activity.isFinishing()) {
                        activity.finish();
                    }
                    if (ret > 0) {
                        Conversation.flushCache();
                        Message.flushCache();
                        SmsReceiver.updateNewMessageNotification(context, null);
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Argument Error", e);
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_LONG).show();
                } catch (SQLiteException e) {
                    Log.e(TAG, "SQL Error", e);
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_LONG).show();
                }

            }
        });
        builder.show();
    }

    /**
     * Add or remove an entry to/from blacklist.
     *
     * @param context {@link Context}
     * @param addr    address
     */
    private static void addToOrRemoveFromSpamlist(final Context context, final String addr) {
        final SpamDB db = new SpamDB(context);
        db.open();
        if (!db.isInDB(addr)) {
            db.insertNr(addr);
            Log.d(TAG, "Added ", addr, " to spam list");
        } else {
            db.removeNr(addr);
            Log.d(TAG, "Removed ", addr, " from spam list");
        }
        db.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_add_contact: // start settings activity
                // TODO, add contact code here
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    static Intent getComposeIntent(final Context context, final String address,
                                   final boolean showChooser) {
        Intent i = null;

        if (!showChooser && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Search for WebSMS
            PackageManager pm = context.getPackageManager();
            i = pm == null ? null : pm.getLaunchIntentForPackage("de.ub0r.android.websms");
        }

        if (i == null) {
            Log.d(TAG, "WebSMS is not installed!");
            i = new Intent(Intent.ACTION_SENDTO);
        }

        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (address == null) {
            i.setData(Uri.parse("sms:"));
        } else {
            i.setData(Uri.parse("smsto:" + PreferencesActivity.fixNumber(context, address)));
        }

        return i;
    }

    /**
     * {@inheritDoc}
     */
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        final Conversation c = Conversation.getConversation(this,
                (Cursor) parent.getItemAtPosition(position), false);
        final Uri target = c.getUri();
        final Intent i = new Intent(this, MessageListActivity.class);
        i.setData(target);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "error launching intent: ", i.getAction(), ", ", i.getData());
            Toast.makeText(this,
                    "error launching messaging app!\n" + "Please contact the developer.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean onItemLongClick(final AdapterView<?> parent, final View view,
                                   final int position, final long id) {
        final Conversation c = Conversation.getConversation(this,
                (Cursor) parent.getItemAtPosition(position), true);
        final Uri target = c.getUri();
        Builder builder = new Builder(this);
        String[] items = longItemClickDialog;
        final Contact contact = c.getContact();
        final String a = contact.getNumber();
        Log.d(TAG, "p: ", a);
        final String n = contact.getName();
        if (TextUtils.isEmpty(n)) {
            builder.setTitle(a);
            items = items.clone();
            items[WHICH_VIEW_CONTACT] = getString(R.string.add_contact_);
        } else {
            builder.setTitle(n);
        }
        final SpamDB db = new SpamDB(getApplicationContext());
        db.open();
        if (db.isInDB(a)) {
            items = items.clone();
            items[WHICH_MARK_SPAM] = getString(R.string.dont_filter_spam_);
        }
        db.close();
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                Intent i;
                try {
                    switch (which) {
                        case WHICH_ANSWER:
                            GroupListActivity.this.startActivity(getComposeIntent(
                                    GroupListActivity.this, a, false));
                            break;
                        case WHICH_CALL:
                            i = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + a));
                            GroupListActivity.this.startActivity(i);
                            break;
                        case WHICH_VIEW_CONTACT:
                            if (n == null) {
                                i = ContactsWrapper.getInstance().getInsertPickIntent(a);
                                Conversation.flushCache();
                            } else {
                                final Uri uri = c.getContact().getUri();
                                i = new Intent(Intent.ACTION_VIEW, uri);
                            }
                            GroupListActivity.this.startActivity(i);
                            break;
                        case WHICH_VIEW:
                            i = new Intent(GroupListActivity.this,
                                    MessageListActivity.class);
                            i.setData(target);
                            GroupListActivity.this.startActivity(i);
                            break;
                        case WHICH_DELETE:
                            ConversationListActivity
                                    .deleteMessages(GroupListActivity.this, target,
                                            R.string.delete_thread_,
                                            R.string.delete_thread_question,
                                            null);
                            break;
                        case WHICH_MARK_SPAM:
                            GroupListActivity.addToOrRemoveFromSpamlist(
                                    GroupListActivity.this, c.getContact().getNumber());
                            break;
                        default:
                            break;
                    }
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "unable to launch activity:", e);
                    Toast.makeText(GroupListActivity.this, R.string.error_unknown,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.create().show();
        return true;
    }

    /**
     * Convert time into formated date.
     *
     * @param context {@link Context}
     * @param time    time
     * @return formated date.
     */
    static String getDate(final Context context, final long time) {
        long t = time;
        if (t < MIN_DATE) {
            t *= MILLIS;
        }
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                PreferencesActivity.PREFS_FULL_DATE, false)) {
            return DateFormat.getTimeFormat(context).format(t) + " "
                    + DateFormat.getDateFormat(context).format(t);
        } else if (t < CAL_DAYAGO.getTimeInMillis()) {
            return DateFormat.getDateFormat(context).format(t);
        } else {
            return DateFormat.getTimeFormat(context).format(t);
        }
    }
}