package org.tigase.messenger.phone.pro.receiver;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.tigase.messenger.phone.pro.R;
import org.tigase.messenger.phone.pro.db.DatabaseContract;
import org.tigase.messenger.phone.pro.roster.PresenceIconMapper;
import org.tigase.messenger.phone.pro.utils.AvatarHelper;
import tigase.jaxmpp.core.client.BareJID;

public class ViewHolder
		extends android.support.v7.widget.RecyclerView.ViewHolder
		implements View.OnClickListener {

	private final ImageView mContactAvatar;
	private final TextView mContactNameView;
	private final ImageView mContactPresence;
	private final TextView mJidView;
	private final ReceiverContextActivity.OnItemSelected selectionHandler;
	private String account;
	private Context context;
	private String jid;

	public ViewHolder(View itemView, ReceiverContextActivity.OnItemSelected selectionHandler) {
		super(itemView);
		this.selectionHandler = selectionHandler;
		this.mJidView = (TextView) itemView.findViewById(R.id.contact_jid);
		this.mContactNameView = (TextView) itemView.findViewById(R.id.contact_display_name);
		this.mContactPresence = (ImageView) itemView.findViewById(R.id.contact_presence);
		this.mContactAvatar = (ImageView) itemView.findViewById(R.id.contact_avatar);

		addClickable(itemView);

	}

	private final void addClickable(final View view) {
		if (view != null) {
			view.setLongClickable(true);
			view.setClickable(true);
			view.setOnClickListener(this);
		}
	}

	public void bind(Context context, Cursor cursor) {
		this.context = context;
		final int id = cursor.getInt(cursor.getColumnIndex(DatabaseContract.RosterItemsCache.FIELD_ID));
		this.jid = cursor.getString(cursor.getColumnIndex(DatabaseContract.RosterItemsCache.FIELD_JID));
		this.account = cursor.getString(cursor.getColumnIndex(DatabaseContract.RosterItemsCache.FIELD_ACCOUNT));
		final String name = cursor.getString(cursor.getColumnIndex(DatabaseContract.RosterItemsCache.FIELD_NAME));
		int status = cursor.getInt(cursor.getColumnIndex(DatabaseContract.RosterItemsCache.FIELD_STATUS));

		mContactNameView.setText(name);
		mContactPresence.setImageResource(PresenceIconMapper.getPresenceResource(status));
		mJidView.setText(jid);

		AvatarHelper.setAvatarToImageView(BareJID.bareJIDInstance(jid), mContactAvatar);
	}

	@Override
	public void onClick(View v) {
		selectionHandler.onItemSelected(account, jid);
	}
}
