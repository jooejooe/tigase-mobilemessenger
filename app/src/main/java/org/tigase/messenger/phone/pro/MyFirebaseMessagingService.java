package org.tigase.messenger.phone.pro;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.tigase.messenger.phone.pro.account.AccountsConstants;
import org.tigase.messenger.phone.pro.account.Authenticator;
import org.tigase.messenger.phone.pro.db.CPresence;
import org.tigase.messenger.phone.pro.service.XMPPService;

import static org.tigase.messenger.phone.pro.service.XMPPService.CONNECT_SINGLE;

public class MyFirebaseMessagingService
		extends FirebaseMessagingService {

	private final static String TAG = "FirebaseMessaging";

	private AccountManager mAccountManager;

	@Override
	public void onCreate() {
		super.onCreate();
		mAccountManager = AccountManager.get(this);
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		Log.d(TAG, "From: " + remoteMessage.getFrom());

		// Check if message contains a data payload.
		if (remoteMessage.getData().size() > 0) {
			Log.d(TAG, "Message data payload: " + remoteMessage.getData());

			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			long presenceId = sharedPref.getLong("presence", CPresence.OFFLINE);

			if (presenceId != CPresence.OFFLINE) {
				Log.i(TAG, "Starting service!");
				Intent ssIntent = new Intent(getApplicationContext(), XMPPService.class);
				String account = remoteMessage.getData().get("account");
				ssIntent.setAction(CONNECT_SINGLE);
				ssIntent.putExtra("fcm", true);
				ssIntent.putExtra("account", account);
				getApplicationContext().startService(ssIntent);
			}

			if (/* Check if data needs to be processed by long running job */ true) {
				// For long-running tasks (10 seconds or more) use Firebase Job Dispatcher.
				//	scheduleJob();
			} else {
				// Handle message within 10 seconds
				//	handleNow();
			}

		}

		// Check if message contains a notification payload.
		if (remoteMessage.getNotification() != null) {
			Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
		}

		// Also if you intend on generating your own notifications as a result of a received FCM
		// message, here is where that should be initiated. See sendNotification method below.

	}

	@Override
	public void onNewToken(String token) {
		Log.d(TAG, "Refreshed token: " + token);

		for (Account account : mAccountManager.getAccountsByType(Authenticator.ACCOUNT_TYPE)) {
			mAccountManager.setUserData(account, AccountsConstants.PUSH_SERVICE_NODE_KEY, null);
			String tmp = mAccountManager.getUserData(account, AccountsConstants.PUSH_NOTIFICATION);
			boolean enabled = tmp == null ? false : Boolean.parseBoolean(tmp);

			Intent action = new Intent(XMPPService.PUSH_NOTIFICATION_CHANGED);
			action.putExtra("account", account);
			action.putExtra("state", (Boolean) enabled);
			sendBroadcast(action);
		}
	}
}
