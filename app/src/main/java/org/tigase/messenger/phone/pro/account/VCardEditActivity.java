package org.tigase.messenger.phone.pro.account;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import org.tigase.messenger.AbstractServiceActivity;
import org.tigase.messenger.phone.pro.R;
import org.tigase.messenger.phone.pro.utils.AvatarHelper;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCard;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;

public class VCardEditActivity
		extends AbstractServiceActivity {

	private static final int PICK_IMAGE = 1;
	private static final int TAKE_PHOTO = 3;
	private String accountName;
	private ImageView avatarImageView;
	private VCard vcard;

	private static void dismissDialog(Activity activity, Dialog dialog) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				if (activity.isDestroyed()) { // or call isFinishing() if min sdk version < 17
					return;
				}
			} else {
				if (activity.isFinishing()) { // or call isFinishing() if min sdk version < 17
					return;
				}
			}
			if (null != dialog && dialog.isShowing()) {
				dialog.dismiss();
			}
		} catch (Throwable e) {
			// be quiet
		}
	}

	private byte[] bitmapToByteArray(Bitmap bmp) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.PNG, 0 /* ignored for PNG */, bos);
		return bos.toByteArray();
	}

	private void fill(@IdRes int fieldId, Consumer<String> c) {
		final View v = findViewById(fieldId);
		if (v instanceof EditText) {
			EditText ed = (EditText) v;
			String s = ed.getText().toString().trim();
			c.accept(s);
		} else {
			c.accept(null);
		}
	}

	private void fillForm(final VCard vcard) {
		if (vcard == null) {
			return;
		}

		try {
			if (vcard.getPhotoVal() != null && vcard.getPhotoVal().length() > 0) {
				byte[] avatar = tigase.jaxmpp.core.client.Base64.decode(vcard.getPhotoVal());

				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				Bitmap bmp1 = BitmapFactory.decodeByteArray(avatar, 0, avatar.length, options);
				if (bmp1 != null) {
					bmp1.recycle();
				}
				// options.inSampleSize = calculateSize(options, 96, 96);
				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				options.inSampleSize = AvatarHelper.calculateSize(options, 50, 50);
				options.inJustDecodeBounds = false;
				Bitmap bmp = BitmapFactory.decodeByteArray(avatar, 0, avatar.length, options);

				ImageView a = (ImageView) findViewById(R.id.contact_avatar);
				a.setImageBitmap(bmp);
			}
		} catch (Exception e) {
			Log.e("tigase", "WTF?", e);
		}

		this.vcard = vcard;
		setValue(vcard.getBday(), R.id.vcard_birthday);
		setValue(vcard.getHomeEmail(), R.id.vcard_email);
//		setValue(vcard.ad,R.id.vcard_address);
		setValue(vcard.getFullName(), R.id.vcard_fullname);
//		setValue(vcard.get,R.id.vcard_homepage);
		setValue(vcard.getHomeTelVoice(), R.id.vcard_mobilephone);
		setValue(vcard.getWorkTelVoice(), R.id.vcard_workphone);
		setValue(vcard.getNickName(), R.id.vcard_nickname);
	}

	private VCard fillVCard() {
		if (vcard == null) {
			vcard = new VCard();
		}

		fill(R.id.vcard_birthday, value -> vcard.setBday(value));
		fill(R.id.vcard_email, value -> vcard.setHomeEmail(value));
		fill(R.id.vcard_fullname, value -> vcard.setFullName(value));
		fill(R.id.vcard_mobilephone, value -> vcard.setHomeTelVoice(value));
		fill(R.id.vcard_workphone, value -> vcard.setWorkTelVoice(value));
		fill(R.id.vcard_nickname, value -> vcard.setNickName(value));

		return vcard;
	}

	@Override
	public void finish() {
		Intent x = new Intent(VCardEditActivity.this, AccountProperties.class);
		x.putExtra("account_name", accountName);
		VCardEditActivity.this.startActivity(x);
		super.finish();
	}

	private Bitmap getScaledImage(Uri uri) {
		try {
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, o);

			// The new size we want to scale to
			final int REQUIRED_SIZE = 128;

			// Find the correct scale value. It should be the power of 2.
			int scale = 1;
			while (o.outWidth / scale / 2 >= REQUIRED_SIZE && o.outHeight / scale / 2 >= REQUIRED_SIZE) {
				scale *= 2;
			}

			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, o2);
		} catch (FileNotFoundException e) {
		}
		return null;
	}

	private void loadVCard() {
		final JaxmppCore jaxmpp = getJaxmpp(accountName);
		if (jaxmpp == null || !jaxmpp.isConnected()) {
			showErrorDialog("Client must be connected to server");
			return;
		}
		final VCardModule module = jaxmpp.getModule(VCardModule.class);
		if (module == null) {
			showErrorDialog("Client must be connected to server");
			return;
		}

		try {
			module.retrieveVCard(JID.jidInstance(accountName), new VCardModule.VCardAsyncCallback() {
				@Override
				public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {

				}

				@Override
				public void onTimeout() throws JaxmppException {

				}

				@Override
				protected void onVCardReceived(VCard vcard) throws XMLException {
					runOnUiThread(() -> fillForm(vcard == null ? new VCard() : vcard));
				}
			});
		} catch (JaxmppException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
			Uri imageUri = data.getData();
			setAvatarFromUri(imageUri);
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.accountName = getIntent().getStringExtra("account");
		setContentView(R.layout.vcard_edit);
		setValue(this.accountName, R.id.vcard_jid);

		this.avatarImageView = (ImageView) findViewById(R.id.contact_avatar);
		avatarImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// showAvatarActionDialog();
				Intent intent = new Intent();
				intent.setType("image/*");
				intent.setAction(Intent.ACTION_GET_CONTENT);
				startActivityForResult(Intent.createChooser(intent, "Select picture"), PICK_IMAGE);
			}
		});

//		AvatarHelper.setAvatarToImageView(BareJID.bareJIDInstance(this.accountName),
//										 );
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.vcardeditor_actions, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.ac_ok:
				send(fillVCard());
				return true;
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onXMPPServiceConnected() {
		if (vcard == null) {
			loadVCard();
		}
	}

	@Override
	protected void onXMPPServiceDisconnected() {

	}

	private void send(VCard vCard) {
		final JaxmppCore jaxmpp = getJaxmpp(accountName);
		if (jaxmpp == null || !jaxmpp.isConnected()) {
			showErrorDialog("Client must be connected to server");
			return;
		}
		final VCardModule module = jaxmpp.getModule(VCardModule.class);
		if (module == null) {
			showErrorDialog("Client must be connected to server");
			return;
		}

		try {
			final ProgressDialog progress = new ProgressDialog(this);
			progress.setMessage("Uploading");
			progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progress.setIndeterminate(true);

			progress.show();
			final IQ iq = IQ.create();
			iq.setType(StanzaType.set);
			iq.addChild(vCard.makeElement());
			jaxmpp.send(iq, new AsyncCallback() {
				@Override
				public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
					runOnUiThread(() -> {
						progress.hide();
						dismissDialog(VCardEditActivity.this, progress);
						showErrorDialog("Cannot send user details to server");
					});
				}

				@Override
				public void onSuccess(Stanza stanza) throws JaxmppException {
					try {
						mServiceConnection.getService()
								.updateVCardHash(getJaxmpp(accountName).getSessionObject(),
												 BareJID.bareJIDInstance(accountName),
												 Base64.decode(vCard.getPhotoVal()));
					} catch (Exception e) {
						Log.e("VcardEdit", "Cannot update VCard cache", e);
					}
					runOnUiThread(() -> {
						progress.hide();
						dismissDialog(VCardEditActivity.this, progress);
						finish();
					});
				}

				@Override
				public void onTimeout() throws JaxmppException {
					runOnUiThread(() -> {
						progress.hide();
						progress.dismiss();
						showErrorDialog("Cannot send user details to server");
					});
				}
			});

		} catch (JaxmppException e) {
			e.printStackTrace();
		}
	}

	private void setAvatarFromUri(Uri uri) {
		if (uri != null) {
			// Link to the image
			Bitmap bmp = getScaledImage(uri);
			avatarImageView.setImageBitmap(bmp);

			byte[] buffer = bitmapToByteArray(bmp);

			vcard.setPhotoVal(Base64.encode(buffer));
			vcard.setPhotoType("image/png");
		}
	}

	private void setValue(String value, @IdRes int toField) {
		View v = findViewById(toField);
		if (v instanceof EditText) {
			((EditText) v).setText(value);
		} else if (v instanceof TextView) {
			((TextView) v).setText(value);
		}
	}

	protected void showAvatarActionDialog() {
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
		builder.setItems(R.array.vcard_avatar_action, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
					case 0:
//										 takePictureForAvatar();
						break;
					case 1:
					default:
						Intent intent = new Intent();
						intent.setType("image/*");
						intent.setAction(Intent.ACTION_GET_CONTENT);
						startActivityForResult(Intent.createChooser(intent, "Select picture"), PICK_IMAGE);
						break;
				}
			}
		});
		builder.create().show();
	}

	private void showErrorDialog(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(VCardEditActivity.this);
		builder.setMessage(msg).setPositiveButton(android.R.string.ok, (dialog, which) -> finish()).show();
	}

	private interface Consumer<T> {

		void accept(T t);
	}
}