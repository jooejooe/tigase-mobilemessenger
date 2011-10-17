package org.tigase.mobile.db.providers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.tigase.mobile.CPresence;
import org.tigase.mobile.XmppService;
import org.tigase.mobile.db.RosterTableMetaData;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceStore;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem.Subscription;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterStore.Predicate;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence.Show;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class RosterProvider extends ContentProvider {

	public static final String AUTHORITY = "org.tigase.mobile.db.providers.RosterProvider";

	public static final String CONTENT_URI = "content://" + AUTHORITY + "/roster";

	private static final boolean DEBUG = false;

	private static final int GROUP_ITEM_URI_INDICATOR = 4;

	public static final String GROUP_URI = "content://" + AUTHORITY + "/groups";

	protected static final int GROUPS_URI_INDICATOR = 3;

	protected static final int ROSTER_ITEM_URI_INDICATOR = 2;

	protected static final int ROSTER_URI_INDICATOR = 1;

	private final static Map<String, String> rosterProjectionMap = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;

		{
			put(RosterTableMetaData.FIELD_ID, RosterTableMetaData.FIELD_ID);
			put(RosterTableMetaData.FIELD_JID, RosterTableMetaData.FIELD_JID);
			put(RosterTableMetaData.FIELD_NAME, RosterTableMetaData.FIELD_NAME);
			put(RosterTableMetaData.FIELD_SUBSCRIPTION, RosterTableMetaData.FIELD_SUBSCRIPTION);
			put(RosterTableMetaData.FIELD_ASK, RosterTableMetaData.FIELD_ASK);
			put(RosterTableMetaData.FIELD_PRESENCE, RosterTableMetaData.FIELD_PRESENCE);
			put(RosterTableMetaData.FIELD_DISPLAY_NAME, RosterTableMetaData.FIELD_DISPLAY_NAME);
		}
	};

	private static final String TAG = "tigase";

	public static String getDisplayName(final BareJID jid) {
		tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem item = XmppService.jaxmpp().getRoster().get(jid);
		return getDisplayName(item);
	}

	public static String getDisplayName(final RosterItem item) {
		if (item == null)
			return null;
		else if (item.getName() != null && item.getName().length() != 0) {
			return item.getName();
		} else {
			return item.getJid().toString();
		}
	}

	public static CPresence getShowOf(final BareJID jid) {
		tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem item = XmppService.jaxmpp().getRoster().get(jid);
		return getShowOf(item);
	}

	public static CPresence getShowOf(final tigase.jaxmpp.core.client.xmpp.modules.roster.RosterItem item) {
		try {
			if (item == null)
				return CPresence.notinroster;
			if (item.isAsk())
				return CPresence.requested;
			if (item.getSubscription() == Subscription.none || item.getSubscription() == Subscription.to)
				return CPresence.offline_nonauth;
			PresenceStore presenceStore = XmppService.jaxmpp().getModulesManager().getModule(PresenceModule.class).getPresence();
			Presence p = presenceStore.getBestPresence(item.getJid());
			CPresence r = CPresence.offline;
			if (p != null) {
				if (p.getType() == StanzaType.unavailable)
					r = CPresence.offline;
				else if (p.getShow() == Show.online)
					r = CPresence.online;
				else if (p.getShow() == Show.away)
					r = CPresence.away;
				else if (p.getShow() == Show.chat)
					r = CPresence.chat;
				else if (p.getShow() == Show.dnd)
					r = CPresence.dnd;
				else if (p.getShow() == Show.xa)
					r = CPresence.xa;
			}
			return r;
		} catch (Exception e) {
			Log.e(TAG, "Can't calculate presence", e);
			return CPresence.error;
		}
	}

	private MessengerDatabaseHelper dbHelper;

	protected final UriMatcher uriMatcher;

	public RosterProvider() {
		this.uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		this.uriMatcher.addURI(AUTHORITY, "roster", ROSTER_URI_INDICATOR);
		this.uriMatcher.addURI(AUTHORITY, "roster/*", ROSTER_ITEM_URI_INDICATOR);
		this.uriMatcher.addURI(AUTHORITY, "groups", GROUPS_URI_INDICATOR);
		this.uriMatcher.addURI(AUTHORITY, "groups/*", GROUP_ITEM_URI_INDICATOR);
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new RuntimeException("There is nothing to delete! uri=" + uri);
	}

	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)) {
		case ROSTER_URI_INDICATOR:
			return RosterTableMetaData.CONTENT_TYPE;
		case GROUPS_URI_INDICATOR:
			return RosterTableMetaData.GROUPS_TYPE;
		case GROUP_ITEM_URI_INDICATOR:
			return RosterTableMetaData.GROUP_ITEM_TYPE;
		case ROSTER_ITEM_URI_INDICATOR:
			return RosterTableMetaData.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new RuntimeException("There is nothing to insert! uri=" + uri);
	}

	@Override
	public boolean onCreate() {
		dbHelper = new MessengerDatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, final String[] selectionArgs, String sortOrder) {
		Cursor c;
		Predicate p = null;
		switch (uriMatcher.match(uri)) {
		case ROSTER_URI_INDICATOR:
			if (selectionArgs != null) {
				final String g = selectionArgs[0];
				p = new Predicate() {

					@Override
					public boolean match(RosterItem item) {
						if (g.equals("All"))
							return true;
						else if (g.equals("default") && item.getGroups().isEmpty())
							return true;
						else
							return item.getGroups().contains(g);
					}
				};
			}

			if (DEBUG)
				Log.d(TAG, "Querying " + uri + " projection=" + Arrays.toString(projection) + "; selection=" + selection
						+ "; selectionArgs=" + Arrays.toString(selectionArgs));

			c = new RosterCursor(p);
			break;
		case GROUPS_URI_INDICATOR:
			c = new GroupsCursor();
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		// int i = c.getCount();
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new RuntimeException("There is nothing to update! uri=" + uri);
	}

}
